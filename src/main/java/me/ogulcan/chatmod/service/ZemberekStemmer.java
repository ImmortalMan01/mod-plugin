package me.ogulcan.chatmod.service;

import zemberek.morphology.TurkishMorphology;
import zemberek.morphology.analysis.WordAnalysis;
import zemberek.morphology.analysis.SingleAnalysis;

public class ZemberekStemmer {
    private static final TurkishMorphology MORPHOLOGY = TurkishMorphology.createWithDefaults();

    public static String lemma(String word) {
        if (word == null || word.isEmpty()) return "";
        WordAnalysis analysis = MORPHOLOGY.analyze(word);
        if (analysis.analysisCount() == 0) return word;
        SingleAnalysis best = analysis.getAnalysisResults().get(0);
        java.util.List<String> lemmas = best.getLemmas();
        if (lemmas.isEmpty()) return word;
        return lemmas.get(0);
    }
}
