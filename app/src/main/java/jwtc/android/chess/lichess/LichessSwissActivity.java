package jwtc.android.chess.lichess;

import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.View;
import android.view.inputmethod.EditorInfo;
import android.widget.AdapterView;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.ProgressBar;
import android.widget.SimpleAdapter;
import android.widget.TextView;
import android.widget.Toast;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.TimeZone;

import jwtc.android.chess.R;
import jwtc.android.chess.helpers.ActivityHelper;
import jwtc.android.chess.lichess.models.SwissStanding;
import jwtc.android.chess.lichess.models.SwissTournament;
import jwtc.android.chess.lichess.models.Team;

/**
 * Team browser + Swiss tournament list/detail. A boardless Lichess screen launched from the lobby;
 * it shares the single service-owned {@link LichessApi} (auth + streams) via {@link LichessBaseActivity}.
 */
public class LichessSwissActivity extends LichessBaseActivity implements AdapterView.OnItemClickListener {
    private static final String TAG = "LichessSwissActivity";
    private static final int VIEW_SWISS_TEAMS = 0, VIEW_SWISS_LIST = 1, VIEW_SWISS_DETAIL = 2;
    private static final long SWISS_REFRESH_INTERVAL_MS = 30_000L;

    private android.widget.ViewAnimator viewAnimatorSwiss;
    private ListView listViewSwissTeams, listViewSwissList, listViewSwissStandings;
    private ProgressBar progressBarSwissTeams, progressBarSwissList, progressBarSwissStandings;
    private SimpleAdapter adapterSwissTeams, adapterSwissList, adapterSwissStandings;
    private final ArrayList<HashMap<String, String>> mapSwissTeams = new ArrayList<>();
    private final ArrayList<HashMap<String, String>> mapSwissList = new ArrayList<>();
    private final ArrayList<HashMap<String, String>> mapSwissStandings = new ArrayList<>();
    private List<Team> swissTeams = new ArrayList<>();
    private List<SwissTournament> swissTournaments = new ArrayList<>();
    private final Set<String> myTeamIds = new HashSet<>();
    private MaterialButton buttonSwissMyTeams, buttonSwissAllTeams, buttonSwissSearch, buttonSwissPrevPage, buttonSwissNextPage, buttonTeamJoinLeave;
    private MaterialButton buttonSwissFilterCreated, buttonSwissFilterStarted, buttonSwissFilterFinished;
    private String swissStatusFilter = "started";
    private EditText editTextTeamSearch;
    private LinearLayout layoutSwissPaging;
    private TextView textViewSwissTeamsStatus, textViewSwissTeamName, textViewSwissListStatus, textViewSwissName, textViewSwissInfo, textViewSwissPage;
    private boolean showingAllTeams = false;
    private int allTeamsPage = 1, allTeamsNbPages = 1;
    private Team currentTeam;
    private SwissTournament currentSwiss;
    private boolean initialized = false;

    private final Handler swissRefreshHandler = new Handler(Looper.getMainLooper());
    // Set true only for a background poll of the detail screen, so onSwissDetail refreshes
    // in place without forcing the view back to the detail child (see onSwissDetail).
    private boolean swissDetailRefresh = false;
    private final Runnable swissRefreshRunnable = new Runnable() {
        @Override
        public void run() {
            if (lichessApi == null) {
                return;
            }
            int child = viewAnimatorSwiss.getDisplayedChild();
            if (child == VIEW_SWISS_LIST && currentTeam != null) {
                lichessApi.fetchTeamSwiss(currentTeam.id, swissStatusFilter);
            } else if (child == VIEW_SWISS_DETAIL && currentSwiss != null) {
                swissDetailRefresh = true;
                lichessApi.fetchSwissDetail(currentSwiss.id);
            }
            swissRefreshHandler.postDelayed(this, SWISS_REFRESH_INTERVAL_MS);
        }
    };

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.lichess_swiss);
        ActivityHelper.fixPaddings(this, findViewById(R.id.ViewAnimatorSwiss));
        setupSwissViews();
    }

    @Override
    protected void onLichessApiConnected(LichessApi api) {
        // The base guarantees the api is authenticated here. Load once per activity instance; view
        // state (current team/tournament, list child) survives a background/resume, so don't reset
        // the user's place on every reconnect.
        if (!initialized) {
            initialized = true;
            showMyTeams();
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        startSwissRefreshLoop();
    }

    @Override
    protected void onPause() {
        super.onPause();
        stopSwissRefreshLoop();
    }

    @Override
    protected void onStop() {
        super.onStop();
        stopSwissRefreshLoop();
    }

    private void setupSwissViews() {
        viewAnimatorSwiss = findViewById(R.id.ViewAnimatorSwiss);

        textViewSwissTeamsStatus = findViewById(R.id.TextViewSwissTeamsStatus);
        textViewSwissTeamName = findViewById(R.id.TextViewSwissTeamName);
        textViewSwissListStatus = findViewById(R.id.TextViewSwissListStatus);
        textViewSwissName = findViewById(R.id.TextViewSwissName);
        textViewSwissInfo = findViewById(R.id.TextViewSwissInfo);
        textViewSwissPage = findViewById(R.id.TextViewSwissPage);
        layoutSwissPaging = findViewById(R.id.LayoutSwissPaging);

        progressBarSwissTeams = findViewById(R.id.ProgressBarSwissTeams);
        progressBarSwissList = findViewById(R.id.ProgressBarSwissList);
        progressBarSwissStandings = findViewById(R.id.ProgressBarSwissStandings);

        buttonSwissMyTeams = findViewById(R.id.ButtonSwissMyTeams);
        buttonSwissMyTeams.setOnClickListener(v -> showMyTeams());
        buttonSwissAllTeams = findViewById(R.id.ButtonSwissAllTeams);
        buttonSwissAllTeams.setOnClickListener(v -> {
            editTextTeamSearch.setText("");
            showAllTeams(1);
        });

        editTextTeamSearch = findViewById(R.id.EditTextTeamSearch);
        editTextTeamSearch.setOnEditorActionListener((v, actionId, event) -> {
            if (actionId == EditorInfo.IME_ACTION_SEARCH) {
                showAllTeams(1);
                return true;
            }
            return false;
        });
        buttonSwissSearch = findViewById(R.id.ButtonSwissSearch);
        buttonSwissSearch.setOnClickListener(v -> showAllTeams(1));

        buttonSwissPrevPage = findViewById(R.id.ButtonSwissPrevPage);
        buttonSwissPrevPage.setOnClickListener(v -> showAllTeams(allTeamsPage - 1));
        buttonSwissNextPage = findViewById(R.id.ButtonSwissNextPage);
        buttonSwissNextPage.setOnClickListener(v -> showAllTeams(allTeamsPage + 1));

        buttonTeamJoinLeave = findViewById(R.id.ButtonTeamJoinLeave);
        buttonTeamJoinLeave.setOnClickListener(v -> onTeamJoinLeaveClicked());

        buttonSwissFilterCreated = findViewById(R.id.ButtonSwissFilterCreated);
        buttonSwissFilterCreated.setOnClickListener(v -> showSwissStatus("created"));
        buttonSwissFilterStarted = findViewById(R.id.ButtonSwissFilterStarted);
        buttonSwissFilterStarted.setOnClickListener(v -> showSwissStatus("started"));
        buttonSwissFilterFinished = findViewById(R.id.ButtonSwissFilterFinished);
        buttonSwissFilterFinished.setOnClickListener(v -> showSwissStatus("finished"));

        findViewById(R.id.ButtonSwissTeamsBack).setOnClickListener(v -> finish());
        findViewById(R.id.ButtonSwissListBack).setOnClickListener(v -> viewAnimatorSwiss.setDisplayedChild(VIEW_SWISS_TEAMS));
        findViewById(R.id.ButtonSwissDetailBack).setOnClickListener(v -> viewAnimatorSwiss.setDisplayedChild(VIEW_SWISS_LIST));

        MaterialButton buttonSwissJoin = findViewById(R.id.ButtonSwissJoin);
        buttonSwissJoin.setOnClickListener(v -> {
            if (currentSwiss == null) {
                return;
            }
            if (currentSwiss.isBoardCompatible()) {
                lichessApi.joinSwiss(currentSwiss.id, null);
            } else {
                Toast.makeText(this, R.string.lichess_swiss_not_board_compatible, Toast.LENGTH_LONG).show();
            }
        });
        MaterialButton buttonSwissWithdraw = findViewById(R.id.ButtonSwissWithdraw);
        buttonSwissWithdraw.setOnClickListener(v -> {
            if (currentSwiss != null) {
                lichessApi.withdrawSwiss(currentSwiss.id);
            }
        });

        adapterSwissTeams = new SimpleAdapter(this, mapSwissTeams, R.layout.lichess_swiss_team_row,
            new String[]{"text_team_name", "text_team_members"},
            new int[]{R.id.text_team_name, R.id.text_team_members});
        listViewSwissTeams = findViewById(R.id.ListViewSwissTeams);
        listViewSwissTeams.setAdapter(adapterSwissTeams);
        listViewSwissTeams.setOnItemClickListener(this);

        adapterSwissList = new SimpleAdapter(this, mapSwissList, R.layout.lichess_swiss_row,
            new String[]{"text_swiss_name", "text_swiss_info"},
            new int[]{R.id.text_swiss_name, R.id.text_swiss_info});
        listViewSwissList = findViewById(R.id.ListViewSwissList);
        listViewSwissList.setAdapter(adapterSwissList);
        listViewSwissList.setOnItemClickListener(this);

        adapterSwissStandings = new SimpleAdapter(this, mapSwissStandings, R.layout.lichess_swiss_standing_row,
            new String[]{"text_rank", "text_player", "text_points"},
            new int[]{R.id.text_rank, R.id.text_player, R.id.text_points});
        listViewSwissStandings = findViewById(R.id.ListViewSwissStandings);
        listViewSwissStandings.setAdapter(adapterSwissStandings);
    }

    private void startSwissRefreshLoop() {
        stopSwissRefreshLoop();
        swissRefreshHandler.postDelayed(swissRefreshRunnable, SWISS_REFRESH_INTERVAL_MS);
    }

    private void stopSwissRefreshLoop() {
        swissRefreshHandler.removeCallbacks(swissRefreshRunnable);
    }

    private void showMyTeams() {
        showingAllTeams = false;
        layoutSwissPaging.setVisibility(View.GONE);
        textViewSwissTeamsStatus.setText(R.string.lichess_swiss_title);
        progressBarSwissTeams.setVisibility(View.VISIBLE);
        lichessApi.fetchMyTeams();
    }

    private void showAllTeams(int page) {
        if (page < 1) {
            page = 1;
        }
        showingAllTeams = true;
        allTeamsPage = page;
        layoutSwissPaging.setVisibility(View.VISIBLE);
        String search = editTextTeamSearch.getText().toString().trim();
        textViewSwissTeamsStatus.setText(search.isEmpty()
            ? getString(R.string.lichess_swiss_all_teams)
            : getString(R.string.lichess_team_search_results, search));
        progressBarSwissTeams.setVisibility(View.VISIBLE);
        lichessApi.fetchAllTeams(page, search);
    }

    private void populateTeams(List<Team> teams) {
        swissTeams = teams;
        mapSwissTeams.clear();
        for (Team team : teams) {
            HashMap<String, String> row = new HashMap<>();
            row.put("text_team_name", team.name != null ? team.name : team.id);
            row.put("text_team_members", getString(R.string.lichess_team_members, team.nbMembers));
            mapSwissTeams.add(row);
        }
        adapterSwissTeams.notifyDataSetChanged();
    }

    private void openTeamDetail(Team team) {
        currentTeam = team;
        textViewSwissTeamName.setText(team.name != null ? team.name : team.id);
        textViewSwissListStatus.setText("");
        mapSwissList.clear();
        adapterSwissList.notifyDataSetChanged();
        refreshTeamJoinLeaveButton();
        viewAnimatorSwiss.setDisplayedChild(VIEW_SWISS_LIST);
        showSwissStatus("started");
    }

    private void showSwissStatus(String status) {
        swissStatusFilter = status;
        buttonSwissFilterCreated.setChecked("created".equals(status));
        buttonSwissFilterStarted.setChecked("started".equals(status));
        buttonSwissFilterFinished.setChecked("finished".equals(status));
        mapSwissList.clear();
        adapterSwissList.notifyDataSetChanged();
        textViewSwissListStatus.setText("");
        if (currentTeam != null) {
            progressBarSwissList.setVisibility(View.VISIBLE);
            lichessApi.fetchTeamSwiss(currentTeam.id, status);
        }
    }

    private boolean isMemberOfCurrentTeam() {
        return currentTeam != null &&
            (myTeamIds.contains(currentTeam.id) || Boolean.TRUE.equals(currentTeam.joined));
    }

    private void refreshTeamJoinLeaveButton() {
        buttonTeamJoinLeave.setText(isMemberOfCurrentTeam() ? R.string.lichess_team_leave : R.string.lichess_team_join);
    }

    private void onTeamJoinLeaveClicked() {
        if (currentTeam == null) {
            return;
        }
        if (isMemberOfCurrentTeam()) {
            lichessApi.quitTeam(currentTeam.id);
        } else {
            openJoinTeamDialog(currentTeam);
        }
    }

    private void openJoinTeamDialog(Team team) {
        LinearLayout container = new LinearLayout(this);
        container.setOrientation(LinearLayout.VERTICAL);
        int pad = (int) (16 * getResources().getDisplayMetrics().density);
        container.setPadding(pad, pad, pad, 0);

        EditText editMessage = new EditText(this);
        editMessage.setHint(R.string.lichess_team_join_message_hint);
        editMessage.setSingleLine(false);
        container.addView(editMessage);

        EditText editPassword = new EditText(this);
        editPassword.setHint(R.string.lichess_team_join_password_hint);
        editPassword.setSingleLine(true);
        container.addView(editPassword);

        new MaterialAlertDialogBuilder(this)
            .setTitle(R.string.lichess_team_join)
            .setView(container)
            .setPositiveButton(R.string.lichess_team_join, (dialog, which) ->
                lichessApi.joinTeam(team.id, editMessage.getText().toString(), editPassword.getText().toString()))
            .setNegativeButton(R.string.button_cancel, null)
            .show();
    }

    private String swissStatusLabel(String status) {
        if ("created".equals(status)) {
            return getString(R.string.lichess_swiss_status_created);
        } else if ("started".equals(status)) {
            return getString(R.string.lichess_swiss_status_started);
        } else if ("finished".equals(status)) {
            return getString(R.string.lichess_swiss_status_finished);
        }
        return status != null ? status : "";
    }

    private static final String[] ISO_DATE_PATTERNS = {
        "yyyy-MM-dd'T'HH:mm:ss'Z'",
        "yyyy-MM-dd'T'HH:mm:ss.SSS'Z'"
    };

    private long parseIsoToMillis(String iso) {
        if (iso == null || iso.isEmpty()) {
            return -1;
        }
        for (String pattern : ISO_DATE_PATTERNS) {
            try {
                SimpleDateFormat fmt = new SimpleDateFormat(pattern, Locale.US);
                fmt.setTimeZone(TimeZone.getTimeZone("UTC"));
                return fmt.parse(iso).getTime();
            } catch (ParseException ignore) {
                // try next pattern
            }
        }
        return -1;
    }

    /** Local start time (e.g. "starts 23:30") for a tournament, or null if unknown. */
    private String swissStartTime(String startsAt) {
        long startMs = parseIsoToMillis(startsAt);
        if (startMs <= 0) {
            return null;
        }
        java.text.DateFormat timeFormat = android.text.format.DateFormat.getTimeFormat(this);
        return getString(R.string.lichess_swiss_starts_at, timeFormat.format(new Date(startMs)));
    }

    private boolean swissBack() {
        int child = viewAnimatorSwiss.getDisplayedChild();
        if (child == VIEW_SWISS_DETAIL) {
            viewAnimatorSwiss.setDisplayedChild(VIEW_SWISS_LIST);
        } else if (child == VIEW_SWISS_LIST) {
            viewAnimatorSwiss.setDisplayedChild(VIEW_SWISS_TEAMS);
        } else {
            finish();
        }
        return true;
    }

    @Override
    public boolean needExitConfirmationDialog() {
        return true;
    }

    @Override
    public void showExitConfirmationDialog() {
        swissBack();
    }

    // --- LichessApiListener (swiss/teams callbacks only) ---

    @Override
    public void onMyTeams(List<Team> teams) {
        myTeamIds.clear();
        for (Team team : teams) {
            myTeamIds.add(team.id);
        }
        if (showingAllTeams) {
            return; // user switched to All teams before this returned
        }
        progressBarSwissTeams.setVisibility(View.GONE);
        populateTeams(teams);
        textViewSwissTeamsStatus.setText(teams.isEmpty()
            ? getString(R.string.lichess_swiss_no_teams)
            : getString(R.string.lichess_swiss_title));
    }

    @Override
    public void onAllTeams(List<Team> teams, int page, int nbPages) {
        if (!showingAllTeams) {
            return;
        }
        progressBarSwissTeams.setVisibility(View.GONE);
        allTeamsPage = page > 0 ? page : allTeamsPage;
        allTeamsNbPages = nbPages > 0 ? nbPages : 1;
        populateTeams(teams);
        textViewSwissPage.setText(getString(R.string.lichess_swiss_page, allTeamsPage, allTeamsNbPages));
        buttonSwissPrevPage.setEnabled(allTeamsPage > 1);
        buttonSwissNextPage.setEnabled(allTeamsPage < allTeamsNbPages);
    }

    @Override
    public void onTeamJoined(String teamId) {
        myTeamIds.add(teamId);
        Toast.makeText(this, getString(R.string.lichess_team_joined,
            currentTeam != null && currentTeam.name != null ? currentTeam.name : teamId), Toast.LENGTH_SHORT).show();
        refreshTeamJoinLeaveButton();
        progressBarSwissList.setVisibility(View.VISIBLE);
        lichessApi.fetchTeamSwiss(teamId, swissStatusFilter);
    }

    @Override
    public void onTeamLeft(String teamId) {
        myTeamIds.remove(teamId);
        if (currentTeam != null) {
            currentTeam.joined = false;
        }
        Toast.makeText(this, getString(R.string.lichess_team_left,
            currentTeam != null && currentTeam.name != null ? currentTeam.name : teamId), Toast.LENGTH_SHORT).show();
        refreshTeamJoinLeaveButton();
    }

    @Override
    public void onSwissList(List<SwissTournament> tournaments) {
        progressBarSwissList.setVisibility(View.GONE);
        if (currentTeam == null) {
            return;
        }
        // Single-status list for the active filter; order by start time (ongoing sort is a no-op here).
        List<SwissTournament> visible = new ArrayList<>(tournaments);
        // Order: ongoing first, then by start time (soonest first).
        Collections.sort(visible, (a, b) -> {
            int rankA = "started".equals(a.status) ? 0 : 1;
            int rankB = "started".equals(b.status) ? 0 : 1;
            if (rankA != rankB) {
                return rankA - rankB;
            }
            return Long.compare(parseIsoToMillis(a.startsAt), parseIsoToMillis(b.startsAt));
        });
        swissTournaments = visible;
        mapSwissList.clear();
        for (SwissTournament t : visible) {
            HashMap<String, String> row = new HashMap<>();
            row.put("text_swiss_name", t.name != null ? t.name : t.id);
            StringBuilder info = new StringBuilder(swissStatusLabel(t.status));
            if ("created".equals(t.status)) {
                String startsAt = swissStartTime(t.startsAt);
                if (startsAt != null) {
                    info.append(" · ").append(startsAt);
                }
            }
            if (t.clock != null) {
                info.append(" · ").append(t.clock.limit / 60).append("+").append(t.clock.increment);
            }
            info.append(" · ").append(getString(R.string.lichess_swiss_players, t.nbPlayers));
            if (!t.isBoardCompatible()) {
                info.append(" · ").append(getString(R.string.lichess_swiss_not_playable));
            }
            row.put("text_swiss_info", info.toString());
            mapSwissList.add(row);
        }
        adapterSwissList.notifyDataSetChanged();
        textViewSwissListStatus.setText(visible.isEmpty()
            ? getString(R.string.lichess_swiss_no_tournaments) : "");
    }

    @Override
    public void onSwissDetail(SwissTournament tournament, List<SwissStanding> standings) {
        progressBarSwissStandings.setVisibility(View.GONE);
        currentSwiss = tournament;
        textViewSwissName.setText(tournament.name != null ? tournament.name : tournament.id);
        String info = getString(R.string.lichess_swiss_detail_info,
            swissStatusLabel(tournament.status), tournament.round, tournament.nbRounds, tournament.nbPlayers);
        if (!tournament.isBoardCompatible()) {
            info += "\n" + getString(R.string.lichess_swiss_not_board_compatible);
        }
        textViewSwissInfo.setText(info);

        // Only rapid/classical standard/chess960 swiss games can be played through the Board API;
        // block joining the rest here (Withdraw stays available for games joined on the web).
        boolean statusJoinable = "created".equals(tournament.status) || "started".equals(tournament.status);
        findViewById(R.id.ButtonSwissJoin).setEnabled(statusJoinable && tournament.isBoardCompatible());
        findViewById(R.id.ButtonSwissWithdraw).setEnabled(statusJoinable);

        mapSwissStandings.clear();
        for (SwissStanding s : standings) {
            HashMap<String, String> row = new HashMap<>();
            row.put("text_rank", "" + s.rank);
            String player = s.title != null && !s.title.isEmpty() ? s.title + " " + s.username : s.username;
            row.put("text_player", player);
            row.put("text_points", "" + s.points);
            mapSwissStandings.add(row);
        }
        adapterSwissStandings.notifyDataSetChanged();
        // A background poll refreshes in place; only an explicit open switches to the detail child,
        // so a poll response landing after the user navigated away won't yank them back.
        if (!swissDetailRefresh) {
            viewAnimatorSwiss.setDisplayedChild(VIEW_SWISS_DETAIL);
        }
        swissDetailRefresh = false;
    }

    @Override
    public void onSwissJoined(String id) {
        Toast.makeText(this, R.string.lichess_swiss_joined, Toast.LENGTH_SHORT).show();
        swissDetailRefresh = false;
        progressBarSwissStandings.setVisibility(View.VISIBLE);
        lichessApi.fetchSwissDetail(id);
    }

    @Override
    public void onSwissError(String message) {
        progressBarSwissTeams.setVisibility(View.GONE);
        progressBarSwissList.setVisibility(View.GONE);
        progressBarSwissStandings.setVisibility(View.GONE);
        Toast.makeText(this, message, Toast.LENGTH_LONG).show();
    }

    @Override
    public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
        if (parent == listViewSwissTeams && swissTeams.size() > position) {
            openTeamDetail(swissTeams.get(position));
        } else if (parent == listViewSwissList && swissTournaments.size() > position) {
            swissDetailRefresh = false;
            progressBarSwissStandings.setVisibility(View.VISIBLE);
            lichessApi.fetchSwissDetail(swissTournaments.get(position).id);
        }
    }
}
