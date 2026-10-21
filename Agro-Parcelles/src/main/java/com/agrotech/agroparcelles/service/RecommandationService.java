package com.agrotech.agroparcelles.service;

import com.agrotech.agroparcelles.dto.reponse.RecommandationResponse;
import com.agrotech.agroparcelles.dto.reponse.RecommandationResponse.*;
import com.agrotech.agroparcelles.dto.reponse.SolDataResponse;
import com.agrotech.agroparcelles.entity.Parcelle;
import com.agrotech.agroparcelles.exception.SatelliteDataException;
import com.agrotech.agroparcelles.service.CatalogueCultures.Culture;
import com.agrotech.agroparcelles.service.CatalogueCultures.RendementReference;
import com.agrotech.agroparcelles.service.ClimatologieService.Climatologie;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Recommande les cultures dont les besoins agronomiques (EcoCrop) correspondent aux conditions
 * de la parcelle, et donne le rendement de référence (FAOSTAT) de chacune.
 *
 * <p>Méthode : pour chaque facteur (température, pluie, pH, texture) on calcule une adéquation
 * entre 0 et 1 (1 dans la plage optimale, décroissance linéaire jusqu'aux limites absolues),
 * puis on retient le facteur le plus limitant (loi du minimum, comme EcoCrop).
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class RecommandationService {

    static final double SCORE_MIN_RETENU = 0.40;
    static final double SCORE_TEXTURE_INADAPTEE = 0.5;
    static final int LIMITE_PAR_DEFAUT = 10;

    private final CatalogueCultures catalogue;
    private final ClimatologieService climatologieService;
    private final SoilService soilService;

    public RecommandationResponse recommander(Parcelle parcelle, int limite) {
        // Le climat est indispensable : sans lui on ne classe rien plutôt que de deviner.
        Climatologie climat = climatologieService.normales(parcelle.getCentroideLat(), parcelle.getCentroideLon());

        Double ph = null;
        String textureClasse = null;
        List<String> avertissements = new ArrayList<>();
        try {
            SolDataResponse sol = soilService.analyser(parcelle);
            ph = sol.getIndicateurs().stream()
                    .filter(i -> "ph".equals(i.code())).map(SolDataResponse.Indicateur::valeur).findFirst().orElse(null);
            textureClasse = sol.getTexture() != null ? sol.getTexture().classe() : null;
        } catch (SatelliteDataException e) {
            log.warn("Sol indisponible pour la parcelle {} : {}", parcelle.getId(), e.getMessage());
        }
        if (ph == null) avertissements.add("pH du sol indisponible : ce facteur n'a pas été évalué, les scores sont moins fiables.");
        if (textureClasse == null) avertissements.add("Texture du sol indisponible : ce facteur n'a pas été évaluée.");

        String categorie = categorieTexture(textureClasse);
        Conditions conditions = new Conditions(arrondi(climat.temperatureMoyenneC(), 1),
                Math.round(climat.pluieAnnuelleMm()), ph, textureClasse, categorie,
                ZoneClimatique.libelle(climat.famille()));

        // Meilleure variété/espèce de chaque culture
        Map<String, Recommandation> meilleures = new HashMap<>();
        for (Culture c : catalogue.cultures()) {
            Recommandation r = evaluer(c, climat, ph, categorie, parcelle.getSuperficie());
            if (r == null) continue;
            meilleures.merge(c.nom(), r, (a, b) -> b.score() > a.score() ? b : a);
        }

        List<Recommandation> resultat = meilleures.values().stream()
                .sorted(Comparator.comparingInt(Recommandation::score).reversed()
                        .thenComparing(Recommandation::culture))
                .limit(Math.max(1, limite))
                .collect(Collectors.toList());

        avertissements.addAll(avertissementsGeneraux());
        return RecommandationResponse.builder()
                .parcelleId(parcelle.getId())
                .nomParcelle(parcelle.getNomParcelle())
                .superficieHa(parcelle.getSuperficie())
                .conditions(conditions)
                .recommandations(resultat)
                .avertissements(avertissements)
                .build();
    }

    /** Null si la culture est trop peu adaptée pour être proposée. */
    static Recommandation evaluer(Culture c, Climatologie climat, Double ph, String categorieTexture, double superficieHa) {
        // Zone climatique : écarte les cultures tempérées d'un climat tropical (et inversement),
        // ce que la température annuelle moyenne seule ne distingue pas.
        if (climat.famille() != null && c.zonesClimatiques() != null && !c.zonesClimatiques().isEmpty()
                && !c.zonesClimatiques().contains(climat.famille())) {
            return null;
        }
        List<Facteur> facteurs = new ArrayList<>();
        facteurs.add(facteur("temperature", "Température moyenne", climat.temperatureMoyenneC(), "°C",
                c.tempMin(), c.tempOptMin(), c.tempOptMax(), c.tempMax()));
        facteurs.add(facteur("pluie", "Pluie annuelle", climat.pluieAnnuelleMm(), "mm/an",
                c.pluieMin(), c.pluieOptMin(), c.pluieOptMax(), c.pluieMax()));
        if (ph != null) {
            facteurs.add(facteur("ph", "pH du sol", ph, "",
                    c.phMin(), c.phOptMin(), c.phOptMax(), c.phMax()));
        }
        if (categorieTexture != null && c.textures() != null && !c.textures().isEmpty()) {
            boolean ok = c.textures().contains(categorieTexture);
            facteurs.add(new Facteur("texture", "Texture du sol (" + categorieTexture + ")", null, "",
                    String.join(", ", c.textures()), "", ok ? 100 : (int) Math.round(SCORE_TEXTURE_INADAPTEE * 100),
                    ok ? "optimal" : "defavorable"));
        }

        Facteur limitant = facteurs.stream().min(Comparator.comparingInt(Facteur::score)).orElseThrow();
        double score = limitant.score() / 100.0;
        if (score < SCORE_MIN_RETENU) return null;

        int pct = limitant.score();
        String niveau = pct >= 80 ? "tres_adaptee" : pct >= 60 ? "adaptee" : "moyenne";
        return new Recommandation(c.nom(), c.nomScientifique(), c.type(), pct, niveau,
                limitant.score() >= 100 ? null : limitant.nom(),
                facteurs, entier(c.cycleJoursMin()), entier(c.cycleJoursMax()),
                rendement(c.rendement(), superficieHa));
    }

    static Facteur facteur(String code, String nom, double valeur, String unite,
                           double absMin, double optMin, double optMax, double absMax) {
        double s = adequation(valeur, absMin, optMin, optMax, absMax);
        String statut = s >= 1.0 ? "optimal" : s > 0 ? "tolere" : "defavorable";
        return new Facteur(code, nom, arrondi(valeur, 1), unite,
                plage(optMin, optMax), plage(absMin, absMax), (int) Math.round(s * 100), statut);
    }

    /** 1 dans la plage optimale, décroissance linéaire jusqu'aux limites absolues, 0 au-delà. */
    static double adequation(double v, double absMin, double optMin, double optMax, double absMax) {
        if (v < absMin || v > absMax) return 0.0;
        if (v >= optMin && v <= optMax) return 1.0;
        if (v < optMin) return optMin == absMin ? 1.0 : (v - absMin) / (optMin - absMin);
        return optMax == absMax ? 1.0 : (absMax - v) / (absMax - optMax);
    }

    /** light = sableux, medium = limoneux/équilibré, heavy = argileux (vocabulaire EcoCrop). */
    static String categorieTexture(String classe) {
        if (classe == null) return null;
        return switch (classe) {
            case "Sableuse", "Sablo-limoneuse" -> "light";
            case "Argileuse", "Limono-argileuse" -> "heavy";
            default -> "medium";
        };
    }

    static Rendement rendement(RendementReference r, double superficieHa) {
        if (r == null) return null;
        if (!r.aDistribution()) {
            return new Rendement(null, null, null, r.ymax(), null, null, null, "indisponible",
                    "Aucune distribution FAOSTAT pour cette culture ; seul le plafond observé est connu.");
        }
        boolean exacte = "exacte".equals(r.correspondance());
        boolean assezDePays = r.nPays() != null && r.nPays() >= 10;
        String fiabilite = exacte && assezDePays ? "moyenne" : "faible";
        String source = String.format(Locale.ROOT,
                "FAOSTAT, rendements africains depuis 2014 (%s, %d observations, %d pays)%s",
                r.produitFao(), r.nObservations(), r.nPays(),
                exacte ? "" : " ; produit FAO proche mais différent de la culture");
        return new Rendement(r.p25(), r.mediane(), r.p75(), r.ymax(),
                arrondi(r.p25() * superficieHa, 1), arrondi(r.mediane() * superficieHa, 1),
                arrondi(r.p75() * superficieHa, 1), fiabilite, source);
    }

    private static List<String> avertissementsGeneraux() {
        return List.of(
                "Le rendement est une référence régionale (quartiles et médiane des rendements africains récents), pas une prédiction pour cette parcelle : variété, fertilisation, irrigation et ravageurs ne sont pas connus.",
                "Les cultures sont filtrées par zone climatique EcoCrop (tropical, sec, subtropical, tempéré) ; les cultures d'altitude et les microclimats ne sont pas distingués.",
                "Le climat utilisé est la normale annuelle (température moyenne, pluie annuelle) : la répartition des pluies dans l'année et la saison de semis ne sont pas évaluées.",
                "Le drainage, la fertilité, le phosphore et le potassium ne sont pas mesurables par satellite et n'entrent pas dans le score : une analyse de sol reste recommandée.");
    }

    private static String plage(double a, double b) {
        return fmt(a) + " – " + fmt(b);
    }

    private static String fmt(double v) {
        return v == Math.rint(v) ? String.valueOf((long) v) : String.format(Locale.ROOT, "%.1f", v);
    }

    private static Integer entier(Double d) {
        return d == null ? null : (int) Math.round(d);
    }

    private static double arrondi(double v, int decimales) {
        double f = Math.pow(10, decimales);
        return Math.round(v * f) / f;
    }
}
