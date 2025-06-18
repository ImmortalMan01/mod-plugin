package me.ogulcan.chatmod.service;

import zemberek.morphology.TurkishMorphology;
import zemberek.morphology.analysis.WordAnalysis;
import zemberek.morphology.analysis.SingleAnalysis;

public class ZemberekStemmer {
    private static TurkishMorphology MORPHOLOGY;

    /**
     * Lazily initialize the Zemberek morphology analyzer. This must run on a
     * thread whose context class loader can access the plugin resources so the
     * tokenization data files are found correctly.
     */
    public static synchronized void init() {
        if (MORPHOLOGY == null) {
            ClassLoader loader = ZemberekStemmer.class.getClassLoader();
            Thread current = Thread.currentThread();
            ClassLoader prev = current.getContextClassLoader();
            try {
                current.setContextClassLoader(loader);
                MORPHOLOGY = TurkishMorphology.createWithDefaults();
            } finally {
                current.setContextClassLoader(prev);
            }
        }
    }

    public static String lemma(String word) {
        if (word == null || word.isEmpty()) return "";
        init();
        WordAnalysis analysis = MORPHOLOGY.analyze(word);
        if (analysis.analysisCount() == 0) return word;
        SingleAnalysis best = analysis.getAnalysisResults().get(0);
        java.util.List<String> lemmas = best.getLemmas();
        if (lemmas.isEmpty()) return word;
        return lemmas.get(0);
    }
}
