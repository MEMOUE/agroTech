package com.agrotech.agroparcelles.service;

/**
 * Famille climatique d'un point (A tropical, B sec, C subtropical, D tempéré, E froid), déduite des
 * 12 moyennes mensuelles de température et de pluie. Même découpage que les zones d'EcoCrop
 * (Köppen/Trewartha), dont seule la lettre de famille est utilisée ici.
 */
public final class ZoneClimatique {

    private ZoneClimatique() {}

    public static String famille(double[] tempMois, double[] pluieMois, double latitude) {
        double tempAnn = moyenne(tempMois);
        double pluieAnn = somme(pluieMois);

        // Moitié chaude de l'année : avril-septembre au nord de l'équateur, octobre-mars au sud.
        double pluieChaude = 0;
        for (int m = 0; m < 12; m++) {
            boolean ete = latitude >= 0 ? (m >= 3 && m <= 8) : (m >= 9 || m <= 2);
            if (ete) pluieChaude += pluieMois[m];
        }
        double partChaude = pluieAnn > 0 ? pluieChaude / pluieAnn : 0.5;
        double seuilSec = partChaude >= 0.7 ? 20 * tempAnn + 280
                : partChaude <= 0.3 ? 20 * tempAnn
                : 20 * tempAnn + 140;
        if (pluieAnn < seuilSec) return "B";

        double min = Double.MAX_VALUE;
        int moisChauds = 0;
        for (double t : tempMois) {
            min = Math.min(min, t);
            if (t >= 10) moisChauds++;
        }
        if (min >= 18) return "A";
        if (moisChauds >= 8) return "C";
        if (moisChauds >= 4) return "D";
        return "E";
    }

    public static String libelle(String famille) {
        if (famille == null) return null;
        return switch (famille) {
            case "A" -> "Tropical (A)";
            case "B" -> "Sec : steppe ou désert (B)";
            case "C" -> "Subtropical (C)";
            case "D" -> "Tempéré (D)";
            case "E" -> "Froid (E)";
            default -> famille;
        };
    }

    private static double moyenne(double[] v) {
        return somme(v) / v.length;
    }

    private static double somme(double[] v) {
        double s = 0;
        for (double x : v) s += x;
        return s;
    }
}
