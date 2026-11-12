package com.agrotech.agroparcelles.service;

import com.agrotech.agroparcelles.dto.reponse.PlanningResponse;
import com.agrotech.agroparcelles.dto.request.ObjectifCultureRequest;
import com.agrotech.agroparcelles.dto.request.ObjectifUpdateRequest;
import com.agrotech.agroparcelles.dto.request.TacheUpdateRequest;
import com.agrotech.agroparcelles.entity.ObjectifCulture;
import com.agrotech.agroparcelles.entity.Parcelle;
import com.agrotech.agroparcelles.entity.TacheCulturale;
import com.agrotech.agroparcelles.exception.ResourceNotFoundException;
import com.agrotech.agroparcelles.repository.ObjectifCultureRepository;
import com.agrotech.agroparcelles.repository.ParcelleRepository;
import com.agrotech.agroparcelles.repository.TacheCulturaleRepository;
import com.agrotech.agroparcelles.service.CatalogueCultures.Culture;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

/**
 * Objectifs de récolte d'une parcelle et planning cultural généré automatiquement pour chacun
 * (voir {@link PlanningGenerator}). Une parcelle peut avoir plusieurs objectifs actifs à la fois
 * (plusieurs cultures en cours — rotation, association de cultures...) : chaque culture a son
 * propre objectif et son propre planning, indépendants des autres.
 */
@Service
@RequiredArgsConstructor
public class ObjectifCultureService {

    private final ObjectifCultureRepository objectifRepository;
    private final TacheCulturaleRepository tacheRepository;
    private final ParcelleRepository parcelleRepository;
    private final CatalogueCultures catalogue;
    private final PlanningGenerator planningGenerator;

    /** Tous les objectifs actifs (une culture en cours chacun) de la parcelle, le plus récent d'abord. */
    @Transactional
    public List<PlanningResponse> getPlannings(Long parcelleId) {
        Double superficie = parcelleRepository.findById(parcelleId).map(Parcelle::getSuperficie).orElse(null);
        return objectifRepository.findByParcelleIdAndStatutOrderByCreatedAtDesc(parcelleId, ObjectifCulture.STATUT_EN_COURS)
                .stream()
                .map(o -> toResponse(o, tacheRepository.findByObjectifIdOrderByOrdreAsc(o.getId()), superficie))
                .toList();
    }

    /** Ajoute une nouvelle culture (objectif + planning) sur la parcelle, sans toucher aux cultures déjà en cours. */
    @Transactional
    public PlanningResponse definirObjectif(Long parcelleId, ObjectifCultureRequest request) {
        Parcelle parcelle = parcelleRepository.findById(parcelleId)
                .orElseThrow(() -> new ResourceNotFoundException("Parcelle introuvable : " + parcelleId));
        if (request.getDateSemisPrevue() == null) {
            throw new IllegalArgumentException("La date de semis prévue est obligatoire.");
        }
        validerPartSurface(parcelleId, request.getPartSurfacePct(), null);

        Optional<Culture> culture = catalogue.trouver(request.getCulture());
        Integer cycleMin = culture.map(c -> arrondi(c.cycleJoursMin())).orElse(null);
        Integer cycleMax = culture.map(c -> arrondi(c.cycleJoursMax())).orElse(null);

        LocalDate dateRecolte = request.getDateRecolteVisee();
        if (dateRecolte == null) {
            Integer cycle = cycleMax != null ? cycleMax : cycleMin;
            if (cycle == null) {
                throw new IllegalArgumentException(
                        "Date de récolte visée obligatoire : le cycle de cette culture n'est pas connu du catalogue.");
            }
            dateRecolte = request.getDateSemisPrevue().plusDays(cycle);
        }

        ObjectifCulture objectif = ObjectifCulture.builder()
                .parcelleId(parcelleId)
                .culture(request.getCulture())
                .cycleJoursMin(cycleMin)
                .cycleJoursMax(cycleMax)
                .dateSemisPrevue(request.getDateSemisPrevue())
                .dateRecolteVisee(dateRecolte)
                .rendementViseTHa(request.getRendementViseTHa())
                .partSurfacePct(request.getPartSurfacePct())
                .statut(ObjectifCulture.STATUT_EN_COURS)
                .build();
        objectif = objectifRepository.save(objectif);

        List<TacheCulturale> taches = planningGenerator.genererTaches(
                objectif.getId(), objectif.getDateSemisPrevue(), objectif.getDateRecolteVisee());
        tacheRepository.saveAll(taches);

        return toResponse(objectif, tacheRepository.findByObjectifIdOrderByOrdreAsc(objectif.getId()), parcelle.getSuperficie());
    }

    /**
     * Ajuste le rendement visé, la date de récolte visée et/ou la part de la parcelle occupée par
     * un objectif actif, sans changer la culture.
     */
    @Transactional
    public PlanningResponse ajusterObjectif(Long parcelleId, Long objectifId, ObjectifUpdateRequest request) {
        ObjectifCulture objectif = getObjectif(parcelleId, objectifId);

        if (request.getRendementViseTHa() != null) {
            objectif.setRendementViseTHa(request.getRendementViseTHa());
        }
        if (request.getPartSurfacePct() != null) {
            validerPartSurface(parcelleId, request.getPartSurfacePct(), objectifId);
            objectif.setPartSurfacePct(request.getPartSurfacePct());
        }
        if (request.getDateRecolteVisee() != null) {
            objectif.setDateRecolteVisee(request.getDateRecolteVisee());
            tacheRepository.findByObjectifIdOrderByOrdreAsc(objectif.getId()).stream()
                    .filter(t -> "recolte".equals(t.getType()) && TacheCulturale.STATUT_A_FAIRE.equals(t.getStatut()))
                    .findFirst()
                    .ifPresent(t -> {
                        t.setDatePrevue(request.getDateRecolteVisee());
                        tacheRepository.save(t);
                    });
        }
        objectifRepository.save(objectif);

        Double superficie = parcelleRepository.findById(parcelleId).map(Parcelle::getSuperficie).orElse(null);
        return toResponse(objectif, tacheRepository.findByObjectifIdOrderByOrdreAsc(objectif.getId()), superficie);
    }

    /**
     * Vérifie que la part de surface demandée, ajoutée à celle déjà allouée aux autres cultures
     * actives de la parcelle, ne dépasse pas 100 %. Les objectifs sans part précisée ne comptent
     * pour rien dans ce total (on ne peut pas deviner leur emprise réelle).
     */
    private void validerPartSurface(Long parcelleId, Double nouvellePart, Long objectifIdAExclure) {
        if (nouvellePart == null) return;
        if (nouvellePart < 0 || nouvellePart > 100) {
            throw new IllegalArgumentException("La part de la parcelle doit être comprise entre 0 et 100 %.");
        }
        double dejaAlloue = objectifRepository.findByParcelleIdAndStatutOrderByCreatedAtDesc(parcelleId, ObjectifCulture.STATUT_EN_COURS)
                .stream()
                .filter(o -> !o.getId().equals(objectifIdAExclure))
                .map(ObjectifCulture::getPartSurfacePct)
                .filter(p -> p != null)
                .mapToDouble(Double::doubleValue)
                .sum();
        if (dejaAlloue + nouvellePart > 100.01) {
            throw new IllegalArgumentException(String.format(java.util.Locale.ROOT,
                    "Le total des parts dépasserait 100%% de la parcelle (déjà %.0f%% alloués aux autres cultures en cours).",
                    dejaAlloue));
        }
    }

    /** Abandonne un objectif (la culture n'est plus suivie) ; conservé en historique, pas supprimé. */
    @Transactional
    public void abandonnerObjectif(Long parcelleId, Long objectifId) {
        ObjectifCulture objectif = getObjectif(parcelleId, objectifId);
        objectif.setStatut(ObjectifCulture.STATUT_ABANDONNE);
        objectifRepository.save(objectif);
    }

    /**
     * Coche (ou décoche) une tâche d'un objectif. Quand la date réelle diffère de la date prévue,
     * les tâches suivantes {@code A_FAIRE} du même objectif sont décalées du même nombre de jours.
     */
    @Transactional
    public PlanningResponse marquerTache(Long parcelleId, Long objectifId, Long tacheId, TacheUpdateRequest request) {
        ObjectifCulture objectif = getObjectif(parcelleId, objectifId);
        TacheCulturale tache = tacheRepository.findById(tacheId)
                .filter(t -> t.getObjectifId().equals(objectif.getId()))
                .orElseThrow(() -> new ResourceNotFoundException("Tâche introuvable : " + tacheId));

        if (request.isFait()) {
            LocalDate dateReelle = request.getDateReelle() != null ? request.getDateReelle() : LocalDate.now();
            long decalageJours = dateReelle.toEpochDay() - tache.getDatePrevue().toEpochDay();
            tache.setDateReelle(dateReelle);
            tache.setStatut(TacheCulturale.STATUT_FAIT);
            tacheRepository.save(tache);

            if (decalageJours != 0) {
                List<TacheCulturale> suivantes = tacheRepository.findByObjectifIdOrderByOrdreAsc(objectif.getId()).stream()
                        .filter(t -> t.getOrdre() > tache.getOrdre() && TacheCulturale.STATUT_A_FAIRE.equals(t.getStatut()))
                        .toList();
                suivantes.forEach(t -> t.setDatePrevue(t.getDatePrevue().plusDays(decalageJours)));
                tacheRepository.saveAll(suivantes);
            }

            if ("recolte".equals(tache.getType())) {
                objectif.setStatut(ObjectifCulture.STATUT_TERMINE);
                objectifRepository.save(objectif);
            }
        } else {
            tache.setDateReelle(null);
            tache.setStatut(TacheCulturale.STATUT_A_FAIRE);
            tacheRepository.save(tache);
        }

        Double superficie = parcelleRepository.findById(parcelleId).map(Parcelle::getSuperficie).orElse(null);
        return toResponse(objectif, tacheRepository.findByObjectifIdOrderByOrdreAsc(objectif.getId()), superficie);
    }

    private ObjectifCulture getObjectif(Long parcelleId, Long objectifId) {
        ObjectifCulture objectif = objectifRepository.findById(objectifId)
                .orElseThrow(() -> new ResourceNotFoundException("Objectif introuvable : " + objectifId));
        if (!objectif.getParcelleId().equals(parcelleId)) {
            throw new ResourceNotFoundException("Objectif introuvable pour la parcelle " + parcelleId);
        }
        return objectif;
    }

    private PlanningResponse toResponse(ObjectifCulture o, List<TacheCulturale> taches, Double superficieParcelle) {
        Double superficieAllouee = (o.getPartSurfacePct() != null && superficieParcelle != null)
                ? Math.round(o.getPartSurfacePct() / 100.0 * superficieParcelle * 100) / 100.0
                : null;
        return PlanningResponse.builder()
                .objectifId(o.getId())
                .parcelleId(o.getParcelleId())
                .culture(o.getCulture())
                .cycleJoursMin(o.getCycleJoursMin())
                .cycleJoursMax(o.getCycleJoursMax())
                .dateSemisPrevue(o.getDateSemisPrevue())
                .dateSemisReelle(o.getDateSemisReelle())
                .dateRecolteVisee(o.getDateRecolteVisee())
                .rendementViseTHa(o.getRendementViseTHa())
                .partSurfacePct(o.getPartSurfacePct())
                .superficieAllouee(superficieAllouee)
                .statut(o.getStatut())
                .taches(taches.stream()
                        .map(t -> new PlanningResponse.Tache(t.getId(), t.getType(), t.getLibelle(), t.getOrdre(),
                                t.getDatePrevue(), t.getDateReelle(), t.getStatut()))
                        .toList())
                .build();
    }

    private static Integer arrondi(Double d) {
        return d == null ? null : (int) Math.round(d);
    }
}
