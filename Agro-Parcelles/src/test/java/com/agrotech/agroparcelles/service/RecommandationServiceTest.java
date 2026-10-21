package com.agrotech.agroparcelles.service;

import com.agrotech.agroparcelles.dto.reponse.RecommandationResponse.Recommandation;
import com.agrotech.agroparcelles.dto.reponse.RecommandationResponse.Rendement;
import com.agrotech.agroparcelles.service.CatalogueCultures.Culture;
import com.agrotech.agroparcelles.service.CatalogueCultures.RendementReference;
import com.agrotech.agroparcelles.service.ClimatologieService.Climatologie;
import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class RecommandationServiceTest {

    private static final Climatologie TROPICAL = new Climatologie(24.0, 1500.0);

    private static Culture culture(String nom, RendementReference r) {
        // temp 10-20-30-38 ; pluie 500-900-1800-3000 ; pH 4.5-5.5-7.0-8.0
        return new Culture(nom, nom + " sp.", "céréale", 90.0, 150.0,
                10.0, 20.0, 30.0, 38.0, 500.0, 900.0, 1800.0, 3000.0, 4.5, 5.5, 7.0, 8.0,
                List.of("medium", "light"), List.of("A", "B"), r);
    }

    @Test
    void adequation_plageOptimaleToleranceEtHorsLimites() {
        assertThat(RecommandationService.adequation(25, 10, 20, 30, 38)).isEqualTo(1.0);
        assertThat(RecommandationService.adequation(15, 10, 20, 30, 38)).isEqualTo(0.5);
        assertThat(RecommandationService.adequation(34, 10, 20, 30, 38)).isEqualTo(0.5);
        assertThat(RecommandationService.adequation(9, 10, 20, 30, 38)).isZero();
        assertThat(RecommandationService.adequation(40, 10, 20, 30, 38)).isZero();
    }

    @Test
    void categorieTexture_traduitLesClassesDuSol() {
        assertThat(RecommandationService.categorieTexture("Sableuse")).isEqualTo("light");
        assertThat(RecommandationService.categorieTexture("Argileuse")).isEqualTo("heavy");
        assertThat(RecommandationService.categorieTexture("Franche (équilibrée)")).isEqualTo("medium");
        assertThat(RecommandationService.categorieTexture(null)).isNull();
    }

    @Test
    void evaluer_cultureAdaptee_scoreMaximal() {
        Recommandation r = RecommandationService.evaluer(culture("Maïs", null), TROPICAL, 6.0, "medium", 2.0);
        assertThat(r).isNotNull();
        assertThat(r.score()).isEqualTo(100);
        assertThat(r.niveau()).isEqualTo("tres_adaptee");
        assertThat(r.facteurLimitant()).isNull();
    }

    @Test
    void evaluer_facteurLimitantEstLePlusFaible() {
        // pH 4.8 : entre 4.5 (abs) et 5.5 (opt) -> 0.3
        Recommandation r = RecommandationService.evaluer(culture("Maïs", null), TROPICAL, 4.8, "medium", 1.0);
        assertThat(r).isNull();   // 30 % < seuil de 40 %

        Recommandation r2 = RecommandationService.evaluer(culture("Maïs", null), TROPICAL, 5.0, "medium", 1.0);
        assertThat(r2).isNotNull();
        assertThat(r2.score()).isEqualTo(50);
        assertThat(r2.facteurLimitant()).isEqualTo("pH du sol");
        assertThat(r2.niveau()).isEqualTo("moyenne");
    }

    @Test
    void evaluer_cultureHorsClimat_ecartee() {
        Climatologie desert = new Climatologie(30.0, 100.0);
        assertThat(RecommandationService.evaluer(culture("Maïs", null), desert, 6.0, "medium", 1.0)).isNull();
    }

    @Test
    void evaluer_sansSol_neJugePasLePhNiLaTexture() {
        Recommandation r = RecommandationService.evaluer(culture("Maïs", null), TROPICAL, null, null, 1.0);
        assertThat(r).isNotNull();
        assertThat(r.facteurs()).extracting("code").containsExactly("temperature", "pluie");
    }

    @Test
    void rendement_intervalleEtProductionPourLaSuperficie() {
        RendementReference ref = new RendementReference("Maize (corn)", "exacte", 5.0, 550, 50, 1.0, 1.5, 2.0);
        Rendement r = RecommandationService.rendement(ref, 4.0);
        assertThat(r.bas()).isEqualTo(1.0);
        assertThat(r.probable()).isEqualTo(1.5);
        assertThat(r.haut()).isEqualTo(2.0);
        assertThat(r.productionProbableT()).isEqualTo(6.0);
        assertThat(r.fiabilite()).isEqualTo("moyenne");
    }

    @Test
    void rendement_correspondanceApproximative_fiabiliteFaible() {
        RendementReference ref = new RendementReference("Bananas", "approximative", 44.0, 300, 30, 5.0, 9.0, 14.0);
        assertThat(RecommandationService.rendement(ref, 1.0).fiabilite()).isEqualTo("faible");
    }

    @Test
    void rendement_sansDistribution_neDonnePasDeValeurProbable() {
        Rendement r = RecommandationService.rendement(new RendementReference(null, null, 3.0, null, null, null, null, null), 1.0);
        assertThat(r.probable()).isNull();
        assertThat(r.potentielMax()).isEqualTo(3.0);
        assertThat(r.fiabilite()).isEqualTo("indisponible");
    }

    @Test
    void catalogueEmbarque_seCharge() throws Exception {
        CatalogueCultures catalogue = new CatalogueCultures();
        catalogue.charger();
        assertThat(catalogue.cultures()).hasSizeGreaterThan(100);
        assertThat(catalogue.cultures()).anyMatch(c -> c.nom().equals("Maïs") && c.rendement().aDistribution());
    }

    @Test
    void catalogueReel_climatEquatorialHumide_proposeDesCulturesTropicales() throws Exception {
        CatalogueCultures catalogue = new CatalogueCultures();
        catalogue.charger();
        Climatologie yaounde = new Climatologie(23.7, 1471.0, "A");

        var top = catalogue.cultures().stream()
                .map(c -> RecommandationService.evaluer(c, yaounde, 5.0, "heavy", 1.0))
                .filter(java.util.Objects::nonNull)
                .sorted(java.util.Comparator.comparingInt(Recommandation::score).reversed())
                .map(Recommandation::culture).distinct().toList();
        System.out.println("TOP YAOUNDE: " + top);

        System.out.println("NB: " + top.size());
        assertThat(top).contains("Manioc", "Cacao");
        assertThat(top).doesNotContain("Seigle", "Poirier", "Noyer", "Olivier", "Pommier");
    }
}
