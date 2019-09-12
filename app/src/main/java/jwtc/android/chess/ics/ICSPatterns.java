package jwtc.android.chess.ics;

import java.util.HashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class ICSPatterns {
    // FICS
    // Challenge: withca (----) GuestFHYH (----) unrated blitz 10 0
    protected static final Pattern challenge = Pattern.compile("Challenge\\: (\\w+) \\((.+)\\) (\\w+) \\((.+)\\) (rated |unrated )(standard |blitz |wild )(\\d+) (\\d+)( \\(adjourned\\))?.*");

    // @TODO ===========================================================================================
    //    C Opponent       On Type          Str  M    ECO Date
    // 1: W jwtc            N [ sr 20   0] 39-39 W3   C44 Thu Nov  5, 12:41 PST 2009
    // 1: B jwtc            Y [ sr  7  12] 39-39 B3   B07 Sun Jun  2, 02:59 PDT 2013
    protected static final Pattern storedRow = Pattern.compile("[\\s]*(\\d+)\\: (W|B) (\\w+)[\\s]*(Y|N).+");
    // =================================================================================================

    // relay
    // :262 GMTopalov         GMCaruana         *       C78

    // GuestNJVN (++++) seeking 5 0 unrated blitz ("play 104" to respond)
    // GuestFXXP (++++) seeking 7 0 unrated blitz f ("play 27" to respond)
    // Suffocate (++++) seeking 30 30 unrated standard [black] m ("play 29" to respond)
    //Pattern _pattSeeking = Pattern.compile("(\\w+) \\((.+)\\) seeking (\\d+) (\\d+) (rated |unrated ?)(standard |blitz |lightning )(\\[white\\] |\\[black\\] )?(f |m )?\\(\"play (\\d+)\" to respond\\)");

    protected static final Pattern chat = Pattern.compile("(\\w+)(\\(\\w+\\))? tells you\\: (.+)");

    //1269.allko                    ++++.kaspalesweb(U)
    protected static final Pattern playerRow = Pattern.compile("(\\s+)?(.{4})([\\.\\:\\^\\ ])(\\w+)(\\(\\w+\\))?");

    protected static final Pattern endGame = Pattern.compile("(\\w+) \\((\\w+)\\) vs. (\\w+) \\((\\w+)\\) --- \\w+ (\\w+\\s+\\d{1,2}, )\\w.*(\\d{4})\\s(\\w.+)\\," +
            " initial time: (\\d{1,3}) minutes, increment: (\\d{1,3})(.|\\n)*\\{(.*)\\} (.*)"); //beginning of game

    // FICS
    //209 1739 rahulso            15  10 unrated standard   [white]     0-9999 m
    //101 ++++ GuestYYLN          16   0 unrated standard               0-9999 mf
    //   6 ++++ sdhisfh             2   0 unrated crazyhouse             0-9999
    //  11 ++++ GuestFGMX          20  10 unrated standard               0-9999 f
    //   7 ++++ Amhztb             10  90 unrated standard               0-9999 m
    //  26 ++++ GuestFFHQ           7   0 unrated wild/3     [white]     0-9999
    protected static final Pattern sought = Pattern.compile("[\\s]*(\\d+)[\\s]+(\\d+|\\++|-+)[\\s]+([\\w\\(\\)]+)[\\s]+(\\d+)[\\s]+(\\d+)[\\s]+(rated|unrated?)[\\s]+([\\w/\\d]+?)[\\s]*(\\[white\\]|\\[black\\])?[\\s]*(\\d+)\\-(\\d+)[\\s]*([fm]+)?");

    // FICS
    //  93 2036 WFMKierzek  2229 FMKarl     [ su120   0]  26:13 -  3:49 (22-22) W: 28
    //  1  2    3           4    5            678     9
    protected static final Pattern gameRow = Pattern.compile("[\\s]*(\\d+) (\\d+) (\\w+)[\\s]+(\\d+) (\\w+)[\\s]+\\[ (s|b|l)(r|u)[\\s]*(\\d+)[\\s]*(\\d+)\\][\\s]*(\\d+):(\\d+)[\\s]*-[\\s]*(\\d+):(\\d+).+");

    protected static final Pattern loggingYouInAs = Pattern.compile("Logging you in as \"(\\w+)\"");
    protected static final Pattern returnToLoginAs = Pattern.compile("Press return to enter the server as \"(\\w+)\":");

    //               1         2       3         4      5      6   7 8
    //Creating: bunnyhopone (++++) mardukedog (++++) unrated blitz 5 5
    protected static final Pattern _pattGameInfo1 = Pattern.compile("\\{?\\w+\\s?\\d+?: (\\w+) \\((.{3,4})\\) (\\w+) \\((.{3,4})\\) (\\w+) (\\w+) (\\d+) (\\d+)");
    protected static final Pattern _pattGameInfo2 = Pattern.compile("\\w+: (\\w+) \\((.{3,4})\\) (\\w+) \\((.{3,4})\\) (\\w+) (\\w+) (\\d+) (\\d+)");
    protected static final Pattern _pattGameInfo3 = Pattern.compile("\\{\\w+\\s(\\d+) \\((\\w+) vs. (\\w+)\\) (.*)\\} (.*)");

    protected static final Pattern gameNumber = Pattern.compile("\\{Game (\\d+) .*");
    protected static final Pattern clock = Pattern.compile("\\((\\d+):(\\d+)\\)");

    public boolean containsGamesDisplayed(String buffer, int lineCount) {
        return lineCount > 3 && buffer.indexOf("\\") == -1 && buffer.indexOf("games displayed") >= 0;
    }

    public boolean containsAdsDisplayed(String buffer, int lineCount) {
        return lineCount > 2 && buffer.indexOf("\\") == -1 && buffer.indexOf("ads displayed.") >= 0;
    }

    public boolean containsPlayersDisplayed(String buffer, int lineCount) {
        return lineCount > 3 && buffer.indexOf("\\") == -1 && buffer.indexOf("players displayed (of ") > 0;
    }

    public boolean isInvalidPassword(String buffer) {
        return buffer.contains("**** Invalid");
    }

    public boolean isSessionStarting(String buffer) {
        return buffer.contains("**** Starting ");
    }

    public String parseGuestHandle(String buffer) {
        Matcher match = returnToLoginAs.matcher(buffer);
        if (match.find()) {
            return match.group(1);
        }
        return null;
    }

    public HashMap<String, String> parseGameLine(String line) {
        Matcher match = gameRow.matcher(line);
        if (match.matches()) {
            HashMap<String, String> item = new HashMap<String, String>();
            //  93 2036 WFMKierzek  2229 FMKarl     [ su120   0]  26:13 -  3:49 (22-22) W: 28
            item.put("nr", match.group(1));
            item.put("text_rating1", match.group(2));
            item.put("text_name1", match.group(3));
            item.put("text_rating2", match.group(4));
            item.put("text_name2", match.group(5));
            item.put("text_type", match.group(6).toUpperCase() + match.group(7).toUpperCase());
            item.put("text_time1", match.group(10) + ":" + match.group(11));
            item.put("text_time2", match.group(12) + ":" + match.group(13));

            return item;
        }
        return null;
    }

    public HashMap<String, String> parsePlayerLine(String line) {
        Matcher match = playerRow.matcher(line);
        if (match.find()) {
            String name = match.group(4);
            if (name != null && match.group(2) != null) {

                String code = match.group(5);
                HashMap<String, String> item = new HashMap<String, String>();

                if (code == null) {
                    item.put("text_name", name);
                    item.put("text_rating", match.group(2));
                } else if (code.equals("(U)") || code.equals("(FM)") || code.equals("(GM)") ||
                            code.equals("(IM)") || code.equals("(WIM)") || code.equals("(WGM)")) {
                    name += code;

                    item.put("text_name", name);
                    item.put("text_rating", match.group(2));
                }
                return item;
            }
        }
        return null;
    }

    public HashMap<String, String> parseGameInfo(String line) {
        if (line.contains("Game") || line.contains("Creating:") || line.contains("Issuing:") || line.contains("Challenge:")) {
            Matcher mat = _pattGameInfo1.matcher(line);
            Matcher mat2 = _pattGameInfo2.matcher(line);

            if (mat.matches() || mat2.matches()) {  //mat and mat2 are the beginning game info
                HashMap<String, String> item = new HashMap<String, String>();
                item.put("whiteHandle", mat.matches() ? mat.group(1) : mat2.group(1));
                String rating = mat.matches() ? mat.group(2) : mat2.group(2);
                if (rating.equals("++++")) {
                    rating = "UNR";
                }
                item.put("whiteRating", rating);
                item.put("blackHandle", mat.matches() ? mat.group(3) : mat2.group(3));
                rating = mat.matches() ? mat.group(4) : mat2.group(4);
                if (rating.equals("++++")) {
                    rating = "UNR";
                }
                item.put("blackRating", rating);
                return item;
            }
        }
        return null;
    }

    public HashMap<String, String> parseBoard(String line) {
        if (line.indexOf("<12> ") >= 0) {
            // this can be multiple lines!
            String[] gameLines = line.split("<12> ");
            HashMap<String, String> item = new HashMap<String, String>();

            if (gameLines[1].contains("none (0:00) none")) {
                item.put("FEN", gameLines[1]);
            }

            for (int j = 0; j < gameLines.length; j++) {
                // at least 65 chars
                if (gameLines[j].length() > 65) {
                    item.put("board", gameLines[j]);
                    break;
                }
            }
            return item;
        }
        return null;
    }

    public boolean isGameInfoEnd(String line) {
        Matcher matcher = _pattGameInfo3.matcher(line);
        return matcher.matches();
    }

    public HashMap<String, String> parseChallenge(String line, String handle) {
        if (line.indexOf("Challenge:") >= 0) {
            Matcher match = challenge.matcher(line);
            if (match.matches()) {
                String opponent, rating;
                if (match.group(1).equals(handle)) {
                    opponent = match.group(3);
                    rating = match.group(4);
                } else {
                    opponent = match.group(1);
                    rating = match.group(2);
                }
                HashMap<String, String> item = new HashMap<String, String>();
                item.put("opponent", opponent);
                item.put("rating", rating);
                item.put("minutes", match.group(7));
                item.put("seconds", match.group(8));
                item.put("type", match.group(5));
                item.put("num", match.group(6));
                return item;
            }
        }
        return null;
    }

    public int getCreatingOrContinuingGameNumber(String line) {
        if(line.indexOf("{Game ") >= 0 && (line.indexOf(" Creating ") > 0 || line.indexOf(" Continuing ") > 0)) {
            Matcher m = gameNumber.matcher(line);
            if (m.matches()) {
                try {
                    return Integer.parseInt(m.group(1));
                } catch (Exception ex) {
                    return 0;
                }
            }
        }
        return 0;
    }

    public boolean isResumingAdjournedGame(String line) {
        return line.indexOf("Creating: ") >= 0 && line.indexOf("(adjourned)") >= 0;
    }

    public boolean isIllegalMove(String line) {
        return line.indexOf("Illegal move (") == 0;
    }

    public boolean isSeekNotAvailable(String line) {
        return line.equals("That seek is not available.");
    }

    public boolean isAbortRequest(String line, String opponent) {
        return !line.contains("\\") && line.indexOf(opponent + " would like to abort the game") >= 0;
    }

    public boolean isAbortedConfirmed(String line) {
        return !line.contains("\\") && line.indexOf("Game aborted by mutual agreement}") >= 0;
    }

    public boolean isAdjournRequest(String line, String opponent) {
        return !line.contains("\\") && line.contains(opponent + " would like to adjourn the game; type \"adjourn\" to accept.");
    }

    public boolean isDrawRequest(String line, String opponent) {
        return !line.contains("\\") && line.contains(opponent + " offers you a draw.");
    }

    public boolean isTakeBackRequest(String line, String opponent) {
        return !line.contains("\\") && line.contains(opponent + " would like to take back ");
    }

    public boolean isAbortedOrAdourned(String line) {
        return line.indexOf("{Game " /*+ getGameNum()*/) >= 0 && line.indexOf("} *") > 0;
    }

    public boolean isAbortOrDrawOrAdjourneRequestSent(String line) {
        return line.equals("Draw request sent.") || line.equals("Abort request sent.") || line.equals("Takeback request sent.");
    }

    public boolean isNowOservingGame(String line) {
        return line.indexOf("You are now observing game") >= 0;
    }

    public boolean isStopObservingGame(String line) {
        return line.indexOf("Removing game") >= 0 && line.indexOf("from observation list") > 0;
    }

    public boolean isStopExaminingGame(String line) {
        return line.indexOf("You are no longer examining game") >= 0;
    }

    public boolean isPuzzleStarted(String line) {
        return line.indexOf("puzzlebot has made you an examiner of game") >= 0;
    }

    public boolean isPuzzleStopped(String line) {
        return line.indexOf("Your current problem has been stopped") >= 0;
    }

    public HashMap<String, String> parseSought(String line) {
        Matcher match = sought.matcher(line);
        if (match.matches()) {

            //Log.i("PATSOUGHT", "groupCount " + match.groupCount());
            if (match.groupCount() > 7) {

                String s, type = match.group(7), rated = match.group(6);
                // 1   2    3                  4   5  6       7          8
                // 209 1739 rahulso            15  10 unrated standard   [white]     0-9999 m
                s = String.format("%2dm+%2ds", Integer.parseInt(match.group(4)), Integer.parseInt(match.group(5)));

                if (type.indexOf("blitz") >= 0 || type.indexOf("standard") >= 0) {
                    HashMap<String, String> item = new HashMap<String, String>();
                    if (type.indexOf("standard") >= 0) {
                        type = "";
                    }
                    item.put("text_game", s + " " + rated + " " + type);
                    item.put("play", match.group(1));
                    item.put("text_name", match.group(3));
                    item.put("text_rating", match.group(2));

                    return item;
                }
            }
        }
        return null;
    }

    public HashMap<String, String> parseGameRow(String line) {
        Matcher match = gameRow.matcher(line);
        if (match.matches()) {
            HashMap<String, String> item = new HashMap<String, String>();
            //  93 2036 WFMKierzek  2229 FMKarl     [ su120   0]  26:13 -  3:49 (22-22) W: 28
            item.put("nr", match.group(1));
            item.put("text_rating1", match.group(2));
            item.put("text_name1", match.group(3));
            item.put("text_rating2", match.group(4));
            item.put("text_name2", match.group(5));
            item.put("text_type", match.group(6).toUpperCase() + match.group(7).toUpperCase());
            item.put("text_time1", match.group(10) + ":" + match.group(11));
            item.put("text_time2", match.group(12) + ":" + match.group(13));
            return item;
        }
        return null;
    }

    public HashMap<String, String> parseStoredRow(String line) {
        Matcher match = storedRow.matcher(line);
        if (match.matches()) {
            HashMap<String, String> item = new HashMap<String, String>();
            item.put("nr_stored", match.group(1));
            item.put("color_stored", match.group(2));
            item.put("text_name_stored", match.group(3));
            item.put("available_stored", match.group(4).equals("Y") ? "*" : "");
            return item;
        }
        return null;
    }
}
