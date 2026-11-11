package jwtc.android.chess.lichess.study;

import java.util.Arrays;
import java.util.List;
import jwtc.android.chess.R;

final class StudyCatalog {
    enum Topic {
        OPENINGS(R.string.study_topic_openings),
        STRATEGY(R.string.study_topic_strategy),
        ENDGAMES(R.string.study_topic_endgames),
        GAMES(R.string.study_topic_games),
        CHAMPIONSHIPS(R.string.study_topic_championships);

        final int label;

        Topic(int label) {
            this.label = label;
        }
    }

    // Public studies featured at https://lichess.org/study/staff-picks.
    static final List<StudyCatalog> STUDIES =
            Arrays.asList(
                    new StudyCatalog(
                            Topic.OPENINGS, "whCVdUeM", "Stafford Gambit Traps", "Eric Rosen"),
                    new StudyCatalog(
                            Topic.OPENINGS,
                            "PDVt7Kbh",
                            "Caro-Kann Defense Repertoire",
                            "Mr_Penings"),
                    new StudyCatalog(Topic.STRATEGY, "kI8ikTU4", "Knights", "NoseKnowsAll"),
                    new StudyCatalog(
                            Topic.STRATEGY,
                            "kjBSgqoA",
                            "Talk to your pieces! Developing plans I",
                            "NoseKnowsAll"),
                    new StudyCatalog(
                            Topic.ENDGAMES,
                            "wukLYIXj",
                            "Beginner Endgames You Must Know!",
                            "NoseKnowsAll"),
                    new StudyCatalog(
                            Topic.ENDGAMES,
                            "bnboDhFM",
                            "Rook Endgames You Must Know!",
                            "NoseKnowsAll"),
                    new StudyCatalog(
                            Topic.GAMES, "GWPZqTsb", "Game of the Week 2021", "Vasif Durarbayli"),
                    // https://lichess.org/page/world-championships
                    championshipBroadcast("238ze7J9", "2025 Ju vs. Tan II"),
                    championshipBroadcast("n3yHJI5Y", "2024 Ding vs. Dommaraju"),
                    championshipBroadcast("lrdHUzyS", "2023 Ding vs. Nepomniachtchi"),
                    championshipBroadcast("2adDCe7c", "2023 Ju vs. Lei"),
                    championshipBroadcast("A6snaCgb", "2021 Carlsen vs. Nepomniachtchi"),
                    championship("8DC5cVBx", "2020 Ju vs. Goryachkina"),
                    championship("7WCFYt0R", "2018 Carlsen vs. Caruana"),
                    championship("fjMKuM8W", "2018 Tan vs. Ju"),
                    championship("edeMUtJW", "2016 Carlsen vs. Karjakin"),
                    championship("l8GckzzE", "2016 Muzychuk M. vs. Hou"),
                    championship("nurfx2OL", "2014 Anand vs. Carlsen II"),
                    championship("PWQ5l7ky", "2013 Anand vs. Carlsen"),
                    championship("tV9n1lY7", "2013 Ushenina vs. Hou"),
                    championship("JSPeC3w6", "2012 Anand vs. Gelfand"),
                    championship("sabfoIzO", "2011 Hou vs. Koneru"),
                    championship("a3SlSwsE", "2010 Anand vs. Topalov"),
                    championship("HALtyMwL", "2008 Anand vs. Kramnik"),
                    championship("LeX7mHMl", "2007 World Championship Tournament"),
                    championship("huDqpKnt", "2006 Kramnik vs. Topalov"),
                    championship("9ubc1elI", "2004 Kramnik vs. Leko"),
                    championship("XAbxnp53", "2000 Kasparov vs. Kramnik"),
                    championship("Ef87T9U8", "1999 Xie vs. Galliamova"),
                    championship("NA28aU3x", "1996 Xie vs. Polgar S."),
                    championship("HpQRsYHb", "1995 Kasparov vs. Anand"),
                    championship("HdrEkPSF", "1993 Kasparov vs. Short"),
                    championship("XsNaAXrS", "1993 Xie vs. Ioseliani"),
                    championship("a2qUVLj3", "1991 Chiburdanidze vs. Xie"),
                    championship("dOfsx9Cq", "1990 Karpov vs. Kasparov V"),
                    championship("MHBZ6RQE", "1988 Chiburdanidze vs. Ioseliani"),
                    championship("dXHewez0", "1987 Karpov vs. Kasparov IV"),
                    championship("4S5UuAGn", "1986 Karpov vs. Kasparov III"),
                    championship("HwxDCW4d", "1986 Chiburdanidze vs. Akhmilovskaya"),
                    championship("b6q7gDGK", "1985 Karpov vs. Kasparov II"),
                    championship("wgJ634pR", "1984 Karpov vs. Kasparov"),
                    championship("IWuGpo0U", "1984 Chiburdanidze vs. Levitina"),
                    championship("G4s4xOR9", "1981 Karpov vs. Korchnoi II"),
                    championship("a1fbAQMn", "1981 Chiburdanidze vs. Alexandria"),
                    championship("iQgHCyqA", "1978 Karpov vs. Korchnoi"),
                    championship("J9GGiLxm", "1978 Gaprindashvili vs. Chiburdanidze"),
                    championship("J2UsvFd1", "1975 Gaprindashvili vs. Alexandria"),
                    championship("Eyl4uwTZ", "1972 Spassky vs. Fischer"),
                    championship("Zk8T60Uk", "1972 Gaprindashvili vs. Kushnir III"),
                    championship("4c6eS6SH", "1969 Petrosian vs. Spassky II"),
                    championship("HXq1eCqO", "1969 Gaprindashvili vs. Kushnir II"),
                    championship("hm6ViybN", "1966 Petrosian vs. Spassky"),
                    championship("TlSMeoSy", "1965 Gaprindashvili vs. Kushnir"),
                    championship("5TfavKIP", "1963 Botvinnik vs. Petrosian"),
                    championship("45nzR0lH", "1962 Bykova vs. Gaprindashvili"),
                    championship("eN0uvvNe", "1961 Botvinnik vs. Tal II"),
                    championship("wC9lnnUr", "1960 Botvinnik vs. Tal"),
                    championship("CmPAhrt7", "1959 Bykova vs. Zvorykina"),
                    championship("AK4EthCc", "1958 Botvinnik vs. Smyslov III"),
                    championship("VnQkhwUy", "1958 Rubtsova vs. Bykova"),
                    championship("MPkpQJkr", "1957 Botvinnik vs. Smyslov II"),
                    championship("3jHEElLY", "1956 FIDE Women's World Championship Tournament"),
                    championship("YL4pmkIt", "1954 Botvinnik vs. Smyslov"),
                    championship("Niha8CRu", "1951 Botvinnik vs. Bronstein"),
                    championship("eA8PlINJ", "1948 World Championship Tournament"),
                    championship("CcnVF8Aj", "1937 Alekhine vs. Euwe II"),
                    championship("vNaOB23W", "1935 Alekhine vs. Euwe"),
                    championship("O9v2NaOU", "1934 Alekhine vs. Bogoljubow II"),
                    championship("wnoAvzBk", "1929 Alekhine vs. Bogoljubow"),
                    championship("G5ogxOsz", "1927 Capablanca vs. Alekhine"),
                    championship("mUCqnOSS", "1921 Lasker vs. Capablanca"),
                    championship("0KNdpNQd", "1910 Lasker vs. Schlechter"),
                    championship("0udqWQdc", "1910 Lasker vs. Janowski"),
                    championship("TFFyOff1", "1908 Lasker vs. Tarrasch"),
                    championship("2k6CI0TT", "1907 Lasker vs. Marshall"),
                    championship("qQ60p6GU", "1896 Lasker vs. Steinitz II"),
                    championship("qyDrsjZ2", "1894 Steinitz vs. Lasker"),
                    championship("G6WWBFU0", "1892 Steinitz vs. Chigorin II"),
                    championship("QFkiT2sJ", "1890 Steinitz vs. Gunsberg"),
                    championship("D9TyW8VV", "1889 Steinitz vs. Chigorin"),
                    championship("beM3pwZa", "1886 Steinitz vs. Zukertort"));

    final Topic topic;
    final String id;
    final String title;
    final String author;
    final boolean broadcast;

    private StudyCatalog(Topic topic, String id, String title, String author) {
        this(topic, id, title, author, false);
    }

    private static StudyCatalog championship(String id, String title) {
        return new StudyCatalog(Topic.CHAMPIONSHIPS, id, title, "Lichess");
    }

    private static StudyCatalog championshipBroadcast(String id, String title) {
        return new StudyCatalog(Topic.CHAMPIONSHIPS, id, title, "Lichess", true);
    }

    private StudyCatalog(Topic topic, String id, String title, String author, boolean broadcast) {
        this.topic = topic;
        this.id = id;
        this.title = title;
        this.author = author;
        this.broadcast = broadcast;
    }
}
