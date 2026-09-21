package com.example.junod6midicontroller;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.media.midi.MidiDevice;
import android.media.midi.MidiDeviceInfo;
import android.media.midi.MidiInputPort;
import android.media.midi.MidiManager;
import android.media.midi.MidiOutputPort;
import android.media.midi.MidiReceiver;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.InputType;
import android.view.Gravity;
import android.view.View;
import android.view.Window;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.PopupWindow;
import android.widget.ScrollView;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;


public class MainActivity extends Activity {
    private static final String PREFS_NAME = "juno_d6_midi_prefs";
    private static final String KEY_BANKS_JSON = "banks_json";
    private static final String KEY_SELECTED_THEME = "selected_theme";

    private static final int THEME_STANDARD = 0;
    private static final int THEME_STANDARD_WHITE = 0;
    private static final int THEME_STANDARD_DARK = 1;

    private static final int ROLAND_DEVICE_ID = 0x10;
    private static final int ROLAND_MODEL_ID_1 = 0x01;
    private static final int ROLAND_MODEL_ID_2 = 0x05;
    private static final int ROLAND_MODEL_ID_3 = 0x0A;
    private static final int ROLAND_COMMAND_RQ1 = 0x11;
    private static final int ROLAND_COMMAND_DT1 = 0x12;

    private static final int SCENE_NAME_LENGTH = 16;
    private static final int USER_SCENE_BANK_MSB = 85;
    private static final int USER_SCENE_BANK_LSB = 0;
    private static final int MIDI_CHANNEL = 15;

    private final List<Bank> banks = new ArrayList<>();
    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private static final long SPLASH_DURATION_MS = 2200L;

    private boolean splashVisible = false;
    private Runnable splashTimeout;

    private static final String KEY_SETLISTS_JSON = "setlists_json";
    private final List<Setlist> setlists = new ArrayList<>();
    private int selectedSetlistIndex = -1;
    private boolean setlistMode = false;

    private Spinner spinnerBanks;
    private LinearLayout layoutPresets;
    private TextView tvEmptyPresets;
    private Button btnNewPreset;
    private Button btnDeleteBank;
    private TextView tvMidiStatus;
    private TextView tvMidiStatusDot;
    private PopupWindow popup;

    private int selectedBankIndex = -1;
    private int activePresetIndex = -1;
    private int selectedTheme = THEME_STANDARD_WHITE;
    private boolean performanceMode = false;
    private boolean performanceFromSetlist = false;

    private MidiManager midiManager;
    private MidiDeviceInfo selectedMidiDeviceInfo;
    private MidiDevice midiDevice;
    private MidiInputPort midiInputPort;
    private MidiOutputPort midiOutputPort;
    private MidiReceiver midiReceiver;

    private ThemePalette palette;

    private boolean waitingForCurrentSceneName = false;
    private Runnable currentSceneNameTimeout;

    private boolean waitingForCurrentSceneMidi = false;
    private Runnable currentSceneMidiTimeout;

    private String pendingCurrentSceneName;
    private int pendingSceneBankMsb = -1;
    private int pendingSceneBankLsb = -1;
    private int pendingSceneProgramChange = -1;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        loadSelectedTheme();
        applySelectedTheme();
        configureSystemBars();
        loadBanks();
        loadSetlists();

        midiManager = (MidiManager) getSystemService(Context.MIDI_SERVICE);
        
        showSplashScreen();
    }

    @Override
    protected void onResume() {
        super.onResume();
        refreshMidiDevices();
        openMidiDeviceIfNeeded();
    }

    @Override
    protected void onPause() {
        cancelSceneRead();
        closeMidiDevice();
        super.onPause();
    }

    @Override
    public void onBackPressed() {
        if (performanceMode) {
            showEditorMode();
        } else if (setlistMode) {
            setlistMode = false;
            showEditorMode();
        } else {
            super.onBackPressed();
        }
    }

    private void loadSelectedTheme() {
        SharedPreferences preferences =
            getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);

        selectedTheme = preferences.getInt(
            KEY_SELECTED_THEME,
            THEME_STANDARD_WHITE
        );

        if (selectedTheme != THEME_STANDARD_WHITE
            && selectedTheme != THEME_STANDARD_DARK) {
            selectedTheme = THEME_STANDARD_WHITE;
        }
    }

    private void saveSelectedTheme() {
        getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                .edit()
                .putInt(KEY_SELECTED_THEME, selectedTheme)
                .apply();
    }

    private void applySelectedTheme() {
        if (selectedTheme == THEME_STANDARD_DARK) {
            palette = new ThemePalette(
                R.drawable.standard_dark,

                Color.rgb(8, 8, 8),
                Color.argb(20, 0, 0, 0),

                Color.argb(225, 22, 22, 22),
                Color.rgb(16, 16, 16),
                Color.rgb(28, 28, 28),

                Color.rgb(255, 92, 0),
                Color.rgb(255, 135, 55),
                Color.rgb(205, 55, 0),

                Color.rgb(245, 245, 245),
                Color.rgb(175, 175, 175),
                Color.rgb(20, 20, 20),

                Color.rgb(75, 75, 75),
                Color.rgb(35, 35, 35),

                Color.rgb(255, 112, 20),
                Color.rgb(145, 55, 10),
                Color.rgb(255, 180, 110)
            );
            return;
        }

        // STANDARD WHITE.
        palette = new ThemePalette(
            R.drawable.standard_white,

            Color.rgb(248, 248, 248),
            Color.argb(8, 255, 255, 255),

            Color.argb(235, 255, 255, 255),
            Color.rgb(242, 242, 242),
            Color.rgb(232, 232, 232),

            Color.rgb(255, 92, 0),
            Color.rgb(255, 135, 55),
            Color.rgb(205, 55, 0),

            Color.rgb(25, 25, 25),
            Color.rgb(95, 95, 95),
            Color.WHITE,

            Color.rgb(195, 195, 195),
            Color.rgb(225, 225, 225),

            Color.rgb(55, 55, 55),
            Color.rgb(130, 130, 130),
            Color.rgb(40, 40, 40)
        );
    }

    private void configureSystemBars() {
        Window window = getWindow();
        window.setStatusBarColor(palette.background);
        window.setNavigationBarColor(palette.background);
        window.getDecorView().setSystemUiVisibility(0);
    }

    private void selectTheme(int theme) {
        selectedTheme = theme;
        saveSelectedTheme();
        applySelectedTheme();
        configureSystemBars();

        if (performanceMode) {
            buildPerformanceScreen();
        } else {
            buildMainScreen();
        }
    }

    private void showEditorMode() {
        performanceMode = false;
        performanceFromSetlist = false;
        activePresetIndex = -1;
        buildMainScreen();
    }

    private void showPerformanceMode() {
        if (setlistMode && selectedSetlistIndex >= 0 && selectedSetlistIndex < setlists.size()) {
            performanceFromSetlist = true;
            performanceMode = true;
            buildPerformanceScreen();
            return;
        }
        if (!hasSelectedBank()) {
            Toast.makeText(
                    this,
                    "Crie ou selecione uma cena antes de entrar no modo Performance.",
                    Toast.LENGTH_SHORT
            ).show();
            return;
        }

        performanceMode = true;
        activePresetIndex = -1;
        buildPerformanceScreen();
    }

    private void sortBanksAlphabetically() {
        Collections.sort(banks, new Comparator<Bank>() {
            @Override
            public int compare(Bank first, Bank second) {
                return first.name.compareToIgnoreCase(second.name);
            }
        });
    }

    private boolean hasSelectedBank() {
        return selectedBankIndex >= 0 && selectedBankIndex < banks.size();
    }

    private FrameLayout createScreenWithBackground() {
        FrameLayout root = new FrameLayout(this);
        root.setBackgroundColor(palette.background);

        ImageView background = new ImageView(this);
        background.setImageResource(palette.backgroundImageResId);
        background.setScaleType(ImageView.ScaleType.CENTER_CROP);

        View overlay = new View(this);
        overlay.setBackgroundColor(palette.screenOverlay);

        root.addView(background, new FrameLayout.LayoutParams(-1, -1));
        root.addView(overlay, new FrameLayout.LayoutParams(-1, -1));

        return root;
    }

    private void showSplashScreen() {
        splashVisible = true;

        FrameLayout root = new FrameLayout(this);

        boolean useDarkSplash =
            selectedTheme == THEME_STANDARD_DARK;

        root.setBackgroundColor(
            useDarkSplash ? Color.BLACK : Color.WHITE
        );

        ImageView splashImage = new ImageView(this);
        splashImage.setImageResource(
            useDarkSplash
                ? R.drawable.splash_dark
                : R.drawable.splash_white
        );
        splashImage.setScaleType(ImageView.ScaleType.CENTER_CROP);
        splashImage.setContentDescription("Tela de abertura");
        splashImage.setClickable(true);

        root.addView(
            splashImage,
            new FrameLayout.LayoutParams(-1, -1)
        );

        View.OnClickListener startListener =
            v -> closeSplashScreen();

        root.setOnClickListener(startListener);
        splashImage.setOnClickListener(startListener);

        setContentView(root);
    }

    private void closeSplashScreen() {
        if (!splashVisible) {
            return;
        }

        splashVisible = false;

        if (splashTimeout != null) {
            mainHandler.removeCallbacks(splashTimeout);
            splashTimeout = null;
        }

        buildMainScreen();
    }

    private void buildMainScreen() {
        performanceMode = false;

        FrameLayout root = createScreenWithBackground();
        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);

        LinearLayout content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(dp(16), dp(14), dp(16), dp(24));
        scroll.addView(content);

        addThemeSelector(content);
        addEditorHeader(content);
        addMidiSection(content);
        addPerformanceBox(content);
        addBankSection(content);
        addPresetSection(content);

        root.addView(scroll, new FrameLayout.LayoutParams(-1, -1));
        setContentView(root);
        configureBankSpinner();
    }

    private void buildPerformanceScreen() {
        FrameLayout root = createScreenWithBackground();

        LinearLayout content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(dp(16), dp(14), dp(16), dp(20));

        TextView title = createPrimaryText("PERFORMANCE MODE", 12, true);
        title.setLetterSpacing(0.10f);
        content.addView(
            title, 
            marginParams(-1, -2, dp(4), 0, dp(4), dp(8))
        );

        // Se estamos em performance a partir de setlist
        if (performanceFromSetlist && selectedSetlistIndex >= 0 && selectedSetlistIndex < setlists.size()) {
            Setlist currentSetlist = setlists.get(selectedSetlistIndex);
            
            // Botão com nome da setlist
            Button setlistNameBtn = createOutlineButton("☰ " + currentSetlist.name.toUpperCase());
            setlistNameBtn.setTextSize(18);
            setlistNameBtn.setOnClickListener(v -> showSetlistSelectorInPerformance(v));
            content.addView(setlistNameBtn, marginParams(-1, dp(52), 0, 0, 0, dp(10)));

            // Indicador de cena atual
            if (currentSetlist.currentMusicIndex >= 0 && 
                currentSetlist.currentMusicIndex < currentSetlist.bankIndices.size()) {
                
                int bankIdx = currentSetlist.bankIndices.get(currentSetlist.currentMusicIndex);
                if (bankIdx >= 0 && bankIdx < banks.size()) {
                    Bank currentBank = banks.get(bankIdx);
                    
                    TextView songInfo = createAccentText(
                        "CENA " + (currentSetlist.currentMusicIndex + 1) + "/" + 
                        currentSetlist.bankIndices.size() + ": " + currentBank.name.toUpperCase(),
                        13,
                        true
                    );
                    songInfo.setGravity(Gravity.CENTER);
                    content.addView(songInfo, marginParams(-1, -2, 0, dp(0), 0, dp(10)));
                }
            }

            // Botões de navegação ANTERIOR / PRÓXIMA
            LinearLayout navRow = new LinearLayout(this);
            navRow.setOrientation(LinearLayout.HORIZONTAL);
            navRow.setGravity(Gravity.CENTER);

            Button prevBtn = createOutlineButton("◀ ANTERIOR");
            prevBtn.setOnClickListener(v -> {
                if (currentSetlist.currentMusicIndex > 0) {
                    currentSetlist.currentMusicIndex--;
                    int bIdx = currentSetlist.bankIndices.get(currentSetlist.currentMusicIndex);
                    if (bIdx >= 0 && bIdx < banks.size()) {
                        selectedBankIndex = bIdx;
                        activePresetIndex = -1;
                        selectSongScene(banks.get(selectedBankIndex));
                        buildPerformanceScreen();
                    }
                }
            });

            Button nextBtn = createOutlineButton("PRÓXIMA ▶");
            nextBtn.setOnClickListener(v -> {
                if (currentSetlist.currentMusicIndex < currentSetlist.bankIndices.size() - 1) {
                    currentSetlist.currentMusicIndex++;
                    int bIdx = currentSetlist.bankIndices.get(currentSetlist.currentMusicIndex);
                    if (bIdx >= 0 && bIdx < banks.size()) {
                        selectedBankIndex = bIdx;
                        activePresetIndex = -1;
                        selectSongScene(banks.get(selectedBankIndex));
                        buildPerformanceScreen();
                    }
                }
            });

            navRow.addView(prevBtn, new LinearLayout.LayoutParams(0, dp(42), 1));
            navRow.addView(nextBtn, marginParams(0, dp(42), dp(10), 0, 0, 0, 1));
            content.addView(navRow, marginParams(-1, dp(42), 0, 0, 0, dp(10)));
        }

        Button song = createSongSelectorButton();
        song.setOnClickListener(v -> showPerformanceSongSelector(song));
        content.addView(song, marginParams(-1, dp(52), 0, 0, 0, dp(10)));

        LinearLayout midi = new LinearLayout(this);
        midi.setGravity(Gravity.CENTER_VERTICAL);
        midi.setPadding(dp(12), dp(8), dp(12), dp(8));
        midi.setBackground(createPanelBackground(
                palette.panelBackground,
                palette.secondaryAccentDark,
                14
        ));

        TextView dot = createSecondaryText("●", 15, false);
        TextView status = createSecondaryText(
                selectedMidiDeviceInfo == null
                        ? "MIDI: aguardando USB-OTG"
                        : "MIDI: " + getMidiDeviceName(selectedMidiDeviceInfo),
                12,
                true
        );
        status.setPadding(dp(8), 0, 0, 0);

        midi.addView(dot);
        midi.addView(status);
        content.addView(midi, marginParams(-1, -2, 0, 0, 0, dp(10)));

        LinearLayout pads = new LinearLayout(this);
        pads.setOrientation(LinearLayout.VERTICAL);
        addPerformancePads(pads);
        content.addView(pads, new LinearLayout.LayoutParams(-1, 0, 1));

        Button back = createOutlineButton("← VOLTAR PARA EDIÇÃO");
        back.setOnClickListener(v -> showEditorMode());
        content.addView(back, marginParams(-1, dp(50), 0, dp(10), 0, 0));

        root.addView(content, new FrameLayout.LayoutParams(-1, -1));
        setContentView(root);
    }

    private void buildSetlistScreen() {
        FrameLayout root = createScreenWithBackground();

        LinearLayout content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(dp(16), dp(14), dp(16), dp(20));

        TextView title = createPrimaryText("PLAYLIST MODE", 12, true);
        title.setLetterSpacing(0.10f);
        content.addView(
            title,
            marginParams(-1, -2, dp(4), 0, dp(4), dp(8))
        );

        // Seletor: exibe o nome da playlist após ela ser escolhida.
        String selectedPlaylistName =
            selectedSetlistIndex >= 0
                && selectedSetlistIndex < setlists.size()
                ? setlists.get(selectedSetlistIndex).name.toUpperCase()
                : "SELECIONAR PLAYLIST";

        Button setlistButton = createOutlineButton(
            selectedPlaylistName + " ▾"
        );
        setlistButton.setTextSize(18);
        setlistButton.setOnClickListener(
            v -> showSetlistSelector(setlistButton)
        );

        content.addView(
            setlistButton,
            marginParams(-1, dp(50), 0, 0, 0, dp(8))
        );

        // Acesso à tela de edição: adicionar/remover/reordenar cenas.
        Button editSetlistButton = createOutlineButton(
            "✏ EDITAR ESTA PLAYLIST"
        );
        editSetlistButton.setTextSize(11);

        editSetlistButton.setOnClickListener(v -> {
            if (selectedSetlistIndex < 0
                || selectedSetlistIndex >= setlists.size()) {
                Toast.makeText(
                    this,
                    "Selecione uma playlist primeiro.",
                    Toast.LENGTH_SHORT
                ).show();
                return;
            }

            showEditSetlistScreen();
        });

        content.addView(
            editSetlistButton,
            marginParams(-1, dp(40), 0, 0, 0, dp(10))
        );

        // Status MIDI.
        LinearLayout midi = new LinearLayout(this);
        midi.setGravity(Gravity.CENTER_VERTICAL);
        midi.setPadding(dp(12), dp(8), dp(12), dp(8));
        midi.setBackground(createPanelBackground(
            palette.panelBackground,
            palette.secondaryAccentDark,
            14
        ));

        TextView dot = createSecondaryText("●", 15, false);

        TextView status = createSecondaryText(
            selectedMidiDeviceInfo == null
                ? "MIDI: aguardando USB-OTG"
                : "MIDI: " + getMidiDeviceName(selectedMidiDeviceInfo),
            12,
            true
        );
        status.setPadding(dp(8), 0, 0, 0);

        midi.addView(dot);
        midi.addView(status);

        content.addView(
            midi,
            marginParams(-1, -2, 0, 0, 0, dp(10))
        );

        // Lista de cenas da playlist.
        if (selectedSetlistIndex >= 0
            && selectedSetlistIndex < setlists.size()) {

            Setlist setlist = setlists.get(selectedSetlistIndex);

            ScrollView scrollScenes = new ScrollView(this);
            scrollScenes.setFillViewport(false);
            scrollScenes.setVerticalScrollBarEnabled(true);

            LinearLayout sceneList = new LinearLayout(this);
            sceneList.setOrientation(LinearLayout.VERTICAL);

            for (int i = 0; i < setlist.bankIndices.size(); i++) {
                final int bankIndex = setlist.bankIndices.get(i);
                final int sceneIndex = i;

                if (bankIndex < 0 || bankIndex >= banks.size()) {
                    continue;
                }

                Bank bank = banks.get(bankIndex);

                Button sceneButton = createOutlineButton(
                    bank.name.toUpperCase()
                );

                sceneButton.setOnClickListener(v -> {
                    setlist.currentMusicIndex = sceneIndex;
                    selectedBankIndex = bankIndex;
                    activePresetIndex = -1;

                    selectSongScene(bank);
                    buildSetlistScreen();
                });

                if (i == setlist.currentMusicIndex) {
                    sceneButton.setBackground(
                        createRoundedBackground(
                            palette.accent,
                            palette.accentBright,
                            18,
                            2
                        )
                    );
                    sceneButton.setTextColor(palette.textDark);
                }

                sceneList.addView(
                    sceneButton,
                    marginParams(-1, dp(48), 0, 0, 0, dp(7))
                );
            }

            scrollScenes.addView(
                sceneList,
                new ScrollView.LayoutParams(
                    ScrollView.LayoutParams.MATCH_PARENT,
                    ScrollView.LayoutParams.WRAP_CONTENT
                )
            );

            content.addView(
                scrollScenes,
                new LinearLayout.LayoutParams(-1, 0, 1)
            );
        } else {
            TextView emptyPlaylist = createSecondaryText(
                "Selecione uma playlist para ver as cenas.",
                14,
                false
            );
            emptyPlaylist.setGravity(Gravity.CENTER);
            emptyPlaylist.setPadding(dp(16), dp(24), dp(16), dp(24));
            emptyPlaylist.setBackground(
                createPanelBackground(
                    palette.panelBackground,
                    palette.border,
                    14
                )
            );

            content.addView(
                emptyPlaylist,
                new LinearLayout.LayoutParams(-1, 0, 1)
            );
        }

        // Abre o Modo Performance usando a primeira cena da playlist.
        Button btnPerformance = createPrimaryActionButton(
            "▶ MODO PERFORMANCE"
        );

        btnPerformance.setOnClickListener(v -> {
            if (selectedSetlistIndex < 0
                || selectedSetlistIndex >= setlists.size()) {
                Toast.makeText(
                    this,
                    "Selecione uma playlist primeiro.",
                    Toast.LENGTH_SHORT
                ).show();
                return;
            }

            Setlist setlist = setlists.get(selectedSetlistIndex);

            if (setlist.bankIndices.isEmpty()) {
                Toast.makeText(
                    this,
                    "Adicione pelo menos uma cena à playlist.",
                    Toast.LENGTH_SHORT
                ).show();
                return;
            }

            setlist.currentMusicIndex = 0;

            int firstBankIndex = setlist.bankIndices.get(0);

            if (firstBankIndex < 0 || firstBankIndex >= banks.size()) {
                Toast.makeText(
                    this,
                    "A primeira cena da playlist não está disponível.",
                    Toast.LENGTH_SHORT
                ).show();
                return;
            }

            selectedBankIndex = firstBankIndex;
            activePresetIndex = -1;

            selectSongScene(banks.get(selectedBankIndex));

            performanceFromSetlist = true;
            performanceMode = true;

            buildPerformanceScreen();
        });

        content.addView(
            btnPerformance,
            marginParams(-1, dp(42), 0, 0, 0, dp(10))
        );

        Button back = createOutlineButton("← VOLTAR PARA EDIÇÃO");
        back.setOnClickListener(v -> {
            setlistMode = false;
            showEditorMode();
        });

        content.addView(
            back,
            marginParams(-1, dp(50), 0, dp(10), 0, 0)
        );

        root.addView(
            content,
            new FrameLayout.LayoutParams(-1, -1)
        );

        setContentView(root);
    }

    private void showEditSetlistScreen() {
        if (selectedSetlistIndex < 0 || selectedSetlistIndex >= setlists.size()) {
            return;
        }

        Setlist setlist = setlists.get(selectedSetlistIndex);

        FrameLayout root = createScreenWithBackground();

        LinearLayout content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(dp(16), dp(14), dp(16), dp(20));

        TextView title = createPrimaryText("EDITAR PLAYLIST", 12, true);
        title.setLetterSpacing(0.10f);
        content.addView(title, marginParams(-1, -2, dp(4), 0, dp(4), dp(8)));

        TextView setName = createAccentText(setlist.name.toUpperCase(), 18, true);
        content.addView(setName, marginParams(-1, -2, 0, dp(4), 0, dp(10)));

        // Botão para adicionar cena
        Button addMusicButton = createPrimaryActionButton("+ ADICIONAR CENA");
        addMusicButton.setOnClickListener(v -> showAddMusicToSetlistDialog(setlist));
        content.addView(addMusicButton, marginParams(-1, dp(42), 0, 0, 0, dp(10)));

        // Lista rolável de cenas da setlist
        ScrollView scrollMusicas = new ScrollView(this);
        scrollMusicas.setFillViewport(false);
        scrollMusicas.setVerticalScrollBarEnabled(true);

        LinearLayout musicasList = new LinearLayout(this);
        musicasList.setOrientation(LinearLayout.VERTICAL);

        for (int i = 0; i < setlist.bankIndices.size(); i++) {
            final int bankIndex = setlist.bankIndices.get(i);
            final int musicIndex = i;
            Bank bank = banks.get(bankIndex);

            LinearLayout row = new LinearLayout(this);
            row.setOrientation(LinearLayout.HORIZONTAL);
            row.setGravity(Gravity.CENTER_VERTICAL);

            Button musicButton = createOutlineButton(bank.name.toUpperCase());
            musicButton.setOnClickListener(v -> {
                // Apenas visualização
            });
            row.addView(musicButton, new LinearLayout.LayoutParams(0, dp(52), 1));

            Button upButton = createOutlineButton("▲");
            upButton.setOnClickListener(v -> {
                setlist.moveBankUp(bankIndex);
                saveSetlists();
                showEditSetlistScreen();
            });
            row.addView(upButton, marginParams(dp(40), dp(52), dp(4), 0, 0, 0));

            Button downButton = createOutlineButton("▼");
            downButton.setOnClickListener(v -> {
                setlist.moveBankDown(bankIndex);
                saveSetlists();
                showEditSetlistScreen();
            });
            row.addView(downButton, marginParams(dp(40), dp(52), dp(4), 0, 0, 0));

            Button removeButton = createOutlineButton("✕");
            removeButton.setOnClickListener(v -> {
                setlist.removeBank(bankIndex);
                saveSetlists();
                showEditSetlistScreen();
            });
            row.addView(removeButton, marginParams(dp(40), dp(52), dp(4), 0, 0, 0));

            musicasList.addView(row, marginParams(-1, dp(60), 0, 0, 0, dp(8)));
        }

        scrollMusicas.addView(
                musicasList,
                new ScrollView.LayoutParams(
                        ScrollView.LayoutParams.MATCH_PARENT,
                        ScrollView.LayoutParams.WRAP_CONTENT
                )
        );

        content.addView(scrollMusicas, new LinearLayout.LayoutParams(-1, 0, 1));

        Button back = createOutlineButton("← VOLTAR PARA PLAYLIST");
        back.setOnClickListener(v -> buildSetlistScreen());
        content.addView(back, marginParams(-1, dp(50), 0, dp(10), 0, 0));

        root.addView(content, new FrameLayout.LayoutParams(-1, -1));
        setContentView(root);
    }

    private void showAddMusicToSetlistDialog(Setlist setlist) {
        if (banks.isEmpty()) {
            Toast.makeText(this, "Crie pelo menos uma cena primeiro.", Toast.LENGTH_LONG).show();
            return;
        }

        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("ADICIONAR CENA À PLAYLIST");

        String[] musicNames = new String[banks.size()];
        for (int i = 0; i < banks.size(); i++) {
            musicNames[i] = banks.get(i).name;
        }

        builder.setItems(musicNames, (dialog, which) -> {
            setlist.addBank(which);
            saveSetlists();
            showEditSetlistScreen();
            Toast.makeText(this, "Cena adicionada: " + banks.get(which).name, Toast.LENGTH_SHORT).show();
        });

        builder.setNegativeButton("CANCELAR", null);
        builder.show();
    }

    private void addThemeSelector(LinearLayout parent) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setPadding(0, 0, 0, dp(12));

        Button white = createThemeButton(
            "STANDARD WHITE",
            THEME_STANDARD_WHITE
        );

        Button dark = createThemeButton(
            "STANDARD DARK",
            THEME_STANDARD_DARK
        );

        white.setOnClickListener(
            v -> selectTheme(THEME_STANDARD_WHITE)
        );

        dark.setOnClickListener(
            v -> selectTheme(THEME_STANDARD_DARK)
        );

        row.addView(
            white,
            new LinearLayout.LayoutParams(0, dp(34), 1)
        );

        row.addView(
            dark,
            marginParams(
                0,
                dp(34),
                dp(8),
                0,
                0,
                0,
                1
            )
        );

        parent.addView(row);
    }

    private Button createThemeButton(String text, int themeId) {
        boolean active = selectedTheme == themeId;
        Button button = new Button(this);

        button.setText(text);
        button.setAllCaps(false);
        button.setTextSize(10);
        button.setTypeface(Typeface.DEFAULT_BOLD);
        button.setGravity(Gravity.CENTER);
        button.setLetterSpacing(0.04f);
        button.setPadding(dp(5), 0, dp(5), 0);

        int accent = getThemeAccent(themeId);
        int bright = getThemeAccentBright(themeId);
        int dark = getThemeAccentDark(themeId);

        if (active) {
            button.setTextColor(getThemeTextDark(themeId));
            button.setBackground(createRaisedBackground(accent, bright, dark, 16));
            applyDarkTextShadow(button);
            applyButtonElevation(button, 4);
        } else {
            button.setTextColor(accent);
            button.setBackground(createDarkRaisedBackground(
                    palette.panelBackground,
                    dark,
                    accent,
                    16
            ));
            applyGlow(button, accent, 1.2f, 100);
            applyButtonElevation(button, 2);
        }

        return button;
    }

    private int getThemeAccent(int id) {
        return Color.rgb(255, 92, 0);
    }

    private int getThemeAccentBright(int id) {
        return Color.rgb(255, 135, 55);
    }

    private int getThemeAccentDark(int id) {
        return Color.rgb(205, 55, 0);
    }

    private int getThemeTextDark(int id) {
        return Color.WHITE;
    }

    private void addEditorHeader(LinearLayout parent) {
        LinearLayout header = new LinearLayout(this);
        header.setOrientation(LinearLayout.VERTICAL);
        header.setPadding(dp(20), dp(8), dp(20), dp(8));
        header.setBackground(createPanelBackground(palette.panelBackground, palette.border, 18));
        
        // Linha principal
        LinearLayout topRow = new LinearLayout(this);
        topRow.setOrientation(LinearLayout.HORIZONTAL);
        topRow.setGravity(Gravity.CENTER_VERTICAL);
        
        // ROLAND
        TextView rolandText = createPrimaryText("ROLAND", 36, true);
        rolandText.setTextColor(palette.accent);
        topRow.addView(rolandText);
        
        // Separador
        TextView separator = createPrimaryText("  |  ", 28, false);
        separator.setTextColor(palette.border);
        topRow.addView(separator);
        
        // Bloco vertical JUNO-D6 + MIDI CONTROLLER
        LinearLayout junoBlock = new LinearLayout(this);
        junoBlock.setOrientation(LinearLayout.VERTICAL);
        
        // JUNO-D6 (grande)
        TextView junoText = createPrimaryText("JUNO-D6", 14, true);
        junoText.setTextColor(palette.textPrimary);
        junoBlock.addView(junoText);
        
        // MIDI CONTROLLER (menor, mesma largura)
        TextView midiText = createSecondaryText("MIDI CONTROLLER", 7, true);
        midiText.setLetterSpacing(0.15f);
        midiText.setPadding(0, dp(2), 0, 0);
        junoBlock.addView(midiText);
        
        topRow.addView(junoBlock);
        
        header.addView(topRow, marginParams(-1, -2, 0, 0, 0, dp(8)));
        
        // Descrição
        TextView description = createSecondaryText(
            "GERENCIE SUAS CENAS, CRIE PRESETS MULTIPARTS E ORGANIZE PLAYLISTS NO JUNO-D6",
            11,
            true
        );
        header.addView(description);
        
        parent.addView(header, marginParams(-1, -2, 0, 0, 0, dp(8)));
    }

    private void addMidiSection(LinearLayout parent) {
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(dp(18), dp(8), dp(18), dp(8));
        card.setBackground(createPanelBackground(palette.panelBackground, palette.border, 14));
        
        // Linha principal: USB-MIDI | STATUS
        LinearLayout topRow = new LinearLayout(this);
        topRow.setOrientation(LinearLayout.HORIZONTAL);
        topRow.setGravity(Gravity.CENTER_VERTICAL);
        
        // USB-MIDI (grande, laranja)
        TextView usbMidiText = createPrimaryText("STATUS USB-MIDI", 18, true);
        usbMidiText.setTextColor(palette.accent);
        topRow.addView(usbMidiText);

        card.addView(topRow);
        
        // Texto do status (menor, abaixo)
        TextView statusDetail = createSecondaryText(
            selectedMidiDeviceInfo == null 
                ? "NENHUM DISPOSITIVO MIDI USB DETECTADO." 
                : getMidiDeviceName(selectedMidiDeviceInfo) + "\nMIDI DETECTADO.",
            11,
            true
        );
        statusDetail.setPadding(0, dp(8), 0, dp(12));
        card.addView(statusDetail);
        
        // Botões
        Button refresh = createPrimaryActionButton("ATUALIZAR DISPOSITIVOS MIDI");
        refresh.setOnClickListener(v -> { refreshMidiDevices(); openMidiDeviceIfNeeded(); });
        card.addView(refresh, new LinearLayout.LayoutParams(-1, dp(34)));
        
        Button readCurrentScene = createPrimaryActionButton("LER CENA ATUAL DO JUNO-D");
        readCurrentScene.setOnClickListener(v -> showReadCurrentSceneConfirmation());
        card.addView(readCurrentScene, marginParams(-1, dp(34), 0, dp(8), 0, 0));
        
        parent.addView(card, marginParams(-1, -2, 0, 0, 0, dp(8)));
    }

    private void addPerformanceBox(LinearLayout parent) {
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(dp(18), dp(10), dp(18), dp(10));
        card.setClickable(true);
        card.setFocusable(true);
        card.setBackground(createPanelBackground(
            palette.panelBackground,
            palette.border,
            14
        ));

        // Toque em qualquer área vazia da caixa abre o Modo Performance.
        card.setOnClickListener(v -> showPerformanceMode());

        TextView title = createPrimaryText("MODO PERFORMANCE", 18, true);
        title.setTextColor(palette.textPrimary);
        title.setClickable(false);
        card.addView(title, marginParams(-1, -2, 0, 0, 0, dp(8)));

        TextView subtitle = createSecondaryText(
            "TOQUE PARA ENTRAR NO MODO PERFORMANCE",
            11f,
            true
        );
        subtitle.setTextColor(palette.textSecondary);
        subtitle.setClickable(false);
        card.addView(subtitle, marginParams(-1, -2, 0, 0, 0, dp(10)));

        // Botão 1: Playlist.
        Button playlistButton = createPrimaryActionButton("⚙ GERENCIAR PLAYLISTS");
        playlistButton.setTextSize(11);
        playlistButton.setSingleLine(true);

        // Impede que o clique no botão também abra o modo performance.
        playlistButton.setOnClickListener(v -> {
            setlistMode = true;
            selectedSetlistIndex = -1;
            buildSetlistScreen();
        });

        card.addView(
            playlistButton,
            marginParams(-1, dp(40), 0, 0, 0, dp(6))
        );

        // Botão 2: Gerenciar Playlist.
        Button managePlaylistButton =
            createPrimaryActionButton("+ CRIAR PLAYLIST");
        managePlaylistButton.setTextSize(11);
        managePlaylistButton.setSingleLine(true);

        // Impede que o clique no botão também abra o modo performance.
        managePlaylistButton.setOnClickListener(
            v -> showCreatePlaylistDialog()
        );

        card.addView(
            managePlaylistButton,
            new LinearLayout.LayoutParams(-1, dp(40))
        );

        parent.addView(
            card,
            marginParams(-1, -2, 0, 0, 0, dp(8))
        );
    }

    private void addBankSection(LinearLayout parent) {
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(dp(18), dp(10), dp(18), dp(8));
        card.setBackground(createPanelBackground(palette.panelBackground, palette.border, 14));
        
        // Linha principal: CENAS
        LinearLayout topRow = new LinearLayout(this);
        topRow.setOrientation(LinearLayout.HORIZONTAL);
        topRow.setGravity(Gravity.CENTER_VERTICAL);
        
        // CENAS (grande, laranja)
        TextView musicasText = createPrimaryText("CENAS", 18, true);
        musicasText.setTextColor(palette.accent);
        topRow.addView(musicasText);
        
        card.addView(topRow);

        // Espaçamento
        View spacer = new View(this);
        spacer.setLayoutParams(new LinearLayout.LayoutParams(-1, dp(10)));
        card.addView(spacer);

        // Spinner
        spinnerBanks = new Spinner(this);
        spinnerBanks.setPadding(dp(8), 0, dp(8), 0);
        spinnerBanks.setBackground(createRoundedBackground(
            palette.panelStrong,
            palette.secondaryAccentDark,
            12,
            1
        ));
        card.addView(spinnerBanks, new LinearLayout.LayoutParams(-1, dp(50)));
        
        // Botões
        LinearLayout buttons = new LinearLayout(this);
        
        Button add = createPrimaryActionButton("+ NOVA CENA");
        add.setOnClickListener(v -> showCreateBankDialog());
        
        btnDeleteBank = createOutlineButton("EXCLUIR");
        btnDeleteBank.setOnClickListener(v -> showDeleteBankDialog());
        
        buttons.addView(add, new LinearLayout.LayoutParams(0, dp(34), 1));
        buttons.addView(btnDeleteBank, marginParams(0, dp(34), dp(8), 0, 0, 0, 1));
        
        card.addView(buttons, marginParams(-1, -2, 0, dp(10), 0, 0));
        parent.addView(card, marginParams(-1, -2, 0, 0, 0, dp(8)));
    }

    private void addPresetSection(LinearLayout parent) {

        btnNewPreset = createPrimaryActionButton("+ NOVO PRESET");
        btnNewPreset.setOnClickListener(v -> {
            if (hasSelectedBank()) {
                showPresetEditor(null, -1);
            }
        });
        parent.addView(btnNewPreset, marginParams(-1, dp(37), 0, 0, 0, dp(10)));

        tvEmptyPresets = createSecondaryText("NENHUM PRESET NESTA CENA", 11, true);
        tvEmptyPresets.setGravity(Gravity.CENTER);
        tvEmptyPresets.setPadding(dp(16), dp(24), dp(16), dp(24));
        tvEmptyPresets.setBackground(createPanelBackground(palette.panelBackground, palette.border, 14));
        parent.addView(tvEmptyPresets, marginParams(-1, -2, 0, 0, 0, dp(8)));

        layoutPresets = new LinearLayout(this);
        layoutPresets.setOrientation(LinearLayout.VERTICAL);
        parent.addView(layoutPresets);
    }

    private String getSelectedBankName() {
        if (hasSelectedBank()) {
            return banks.get(selectedBankIndex).name;
        }

        return "SEM CENA";
    }

    private Button createSongSelectorButton() {
        Button button = createOutlineButton(getSelectedBankName().toUpperCase() + "  ▾");
        button.setTextSize(22);
        return button;
    }

    private void showPerformanceSongSelector(View anchor) {
        if (banks.isEmpty()) {
            return;
        }

        LinearLayout content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(dp(6), dp(6), dp(6), dp(6));

        // Declara popup antes, mas ainda sem conteúdo
        final PopupWindow[] popupRef = new PopupWindow[1];

        for (int i = 0; i < banks.size(); i++) {
            final int index = i;
            Button button = createOutlineButton(banks.get(i).name.toUpperCase());
            button.setOnClickListener(v -> {
                selectedBankIndex = index;
                activePresetIndex = -1;
                selectSongScene(banks.get(index));
                if (popupRef[0] != null) {
                    popupRef[0].dismiss();
                }
                buildPerformanceScreen();
            });
            content.addView(button, marginParams(-1, dp(52), 0, 0, 0, dp(4)));
        }

        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(false);
        scroll.setVerticalScrollBarEnabled(true);
        scroll.setOverScrollMode(View.OVER_SCROLL_IF_CONTENT_SCROLLS);

        scroll.addView(
                content,
                new ScrollView.LayoutParams(
                        ScrollView.LayoutParams.MATCH_PARENT,
                        ScrollView.LayoutParams.WRAP_CONTENT
                )
        );

        popupRef[0] = new PopupWindow(
                scroll,
                anchor.getWidth(),
                dp(400),
                true
        );

        popupRef[0].setBackgroundDrawable(createRoundedBackground(
                palette.panelStrong,
                palette.secondaryAccentDark,
                16,
                1
        ));
        popupRef[0].setOutsideTouchable(true);

        popupRef[0].showAsDropDown(anchor, 0, dp(4));
    }

    private void showSetlistSelectorInPerformance(View anchor) {
        if (setlists.isEmpty()) {
            Toast.makeText(this, "Crie pelo menos uma Playlist primeiro.", Toast.LENGTH_LONG).show();
            return;
        }

        LinearLayout content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(dp(6), dp(6), dp(6), dp(6));

        for (int i = 0; i < setlists.size(); i++) {
            final int index = i;
            final Setlist setlist = setlists.get(i);

            Button button = createOutlineButton(setlist.name.toUpperCase());
            button.setOnClickListener(v -> {
                selectedSetlistIndex = index;
                setlist.currentMusicIndex = 0;
                if (!setlist.bankIndices.isEmpty()) {
                    int firstBankIdx = setlist.bankIndices.get(0);
                    if (firstBankIdx >= 0 && firstBankIdx < banks.size()) {
                        selectedBankIndex = firstBankIdx;
                        activePresetIndex = -1;
                        selectSongScene(banks.get(selectedBankIndex));
                    }
                }
                performanceFromSetlist = true;
                buildPerformanceScreen();
                if (popup != null) popup.dismiss();
            });
            content.addView(button, marginParams(-1, dp(52), 0, 0, 0, dp(4)));
        }

        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(false);
        scroll.setVerticalScrollBarEnabled(true);
        scroll.addView(
            content,
            new ScrollView.LayoutParams(
                ScrollView.LayoutParams.MATCH_PARENT,
                ScrollView.LayoutParams.WRAP_CONTENT
            )
        );

        popup = new PopupWindow(
            scroll,
            anchor.getWidth(),
            dp(400),
            true
        );

        popup.setBackgroundDrawable(createRoundedBackground(
            palette.panelStrong,
            palette.secondaryAccentDark,
            16,
            1
        ));
        popup.setOutsideTouchable(true);
        popup.showAsDropDown(anchor, 0, dp(4));
    }

    private void showSetlistSelector(View anchor) {
        if (setlists.isEmpty()) {
            Toast.makeText(this, "Crie pelo menos uma playlist primeiro.", Toast.LENGTH_LONG).show();
            return;
        }

        LinearLayout content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(dp(6), dp(6), dp(6), dp(6));

        for (int i = 0; i < setlists.size(); i++) {
            final int index = i;
            final Setlist setlist = setlists.get(i);

            LinearLayout row = new LinearLayout(this);
            row.setOrientation(LinearLayout.HORIZONTAL);
            row.setGravity(Gravity.CENTER_VERTICAL);

            Button button = createOutlineButton(setlist.name.toUpperCase());
            button.setOnClickListener(v -> {
                selectedSetlistIndex = index;
                setlist.currentMusicIndex = -1;
                selectedBankIndex = -1;
                activePresetIndex = -1;
                buildSetlistScreen();
                popup.dismiss();
            });

            Button deleteButton = createOutlineButton("X");
            deleteButton.setOnClickListener(v -> {
                new AlertDialog.Builder(this)
                        .setTitle("Excluir Playlist")
                        .setMessage("Tem certeza que deseja excluir \"" + setlist.name + "\"?")
                        .setPositiveButton("Excluir", (dialog, which) -> {
                            setlists.remove(index);
                            saveSetlists();
                            popup.dismiss();
                        })
                        .setNegativeButton("Cancelar", null)
                        .show();
            });

            deleteButton.setTextSize(9);
            deleteButton.setPadding(dp(4), 0, dp(4), 0);

            row.addView(
                button,
                new LinearLayout.LayoutParams(0, dp(42), 1)
            );

            row.addView(
                deleteButton,
                marginParams(dp(42), dp(42), dp(6), 0, 0, 0)
            );

            content.addView(
                row,
                marginParams(-1, dp(48), 0, 0, 0, dp(4))
            );
        }

        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(false);
        scroll.setVerticalScrollBarEnabled(true);
        scroll.setOverScrollMode(View.OVER_SCROLL_IF_CONTENT_SCROLLS);

        scroll.addView(
                content,
                new ScrollView.LayoutParams(
                        ScrollView.LayoutParams.MATCH_PARENT,
                        ScrollView.LayoutParams.WRAP_CONTENT
                )
        );

        popup = new PopupWindow(
                scroll,
                anchor.getWidth(),
                dp(400),
                true
        );

        popup.setBackgroundDrawable(createRoundedBackground(
                palette.panelStrong,
                palette.secondaryAccentDark,
                16,
                1
        ));
        popup.setOutsideTouchable(true);

        popup.showAsDropDown(anchor, 0, dp(4));
    }

    private void showCreatePlaylistDialog() {
        LinearLayout form = new LinearLayout(this);
        form.setOrientation(LinearLayout.VERTICAL);
        form.setPadding(dp(18), dp(8), dp(18), dp(14));

        TextView label = createDialogLabel("NOME DA PLAYLIST");
        form.addView(label);

        EditText inputName = createThemeEditText(
            "Ex.: Madame 19/08"
        );
        form.addView(inputName);

        AlertDialog dialog = new AlertDialog.Builder(this)
            .setTitle("CRIAR PLAYLIST")
            .setView(form)
            .setNegativeButton("CANCELAR", null)
            .setPositiveButton("CRIAR", null)
            .create();

        dialog.setOnShowListener(v -> {
            Button createButton =
                dialog.getButton(AlertDialog.BUTTON_POSITIVE);

            createButton.setOnClickListener(view -> {
                String name =
                    inputName.getText().toString().trim();

                if (name.isEmpty()) {
                    inputName.setError("Informe um nome.");
                    inputName.requestFocus();
                    return;
                }

                Setlist playlist = new Setlist(name);
                setlists.add(playlist);
                saveSetlists();

                selectedSetlistIndex = setlists.size() - 1;

                Toast.makeText(
                    this,
                    "Playlist criada: " + name,
                    Toast.LENGTH_SHORT
                ).show();

                dialog.dismiss();

                // Leva à Playlist Mode já com a nova playlist selecionada.
                setlistMode = true;
                buildSetlistScreen();
            });
        });

        dialog.show();
    }

    private void showManageSetlistsDialog() {
        LinearLayout form = new LinearLayout(this);
        form.setOrientation(LinearLayout.VERTICAL);
        form.setPadding(dp(18), dp(8), dp(18), dp(14));

        form.addView(createDialogLabel("CRIAR NOVA PLAYLIST"));

        EditText inputName = createThemeEditText("Ex.: Show The Cure");
        form.addView(inputName);

        AlertDialog dialog = new AlertDialog.Builder(this)
                .setTitle("GERENCIAR PLAYLISTS")
                .setView(form)
                .setNegativeButton("CANCELAR", null)
                .setPositiveButton("CRIAR", null)
                .create();

        dialog.setOnShowListener(v -> dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(x -> {
            String name = inputName.getText().toString().trim();

            if (name.isEmpty()) {
                inputName.setError("Informe um nome.");
                inputName.requestFocus();
                return;
            }

            Setlist setlist = new Setlist(name);
            setlists.add(setlist);
            saveSetlists();

            selectedSetlistIndex = setlists.size() - 1;
            setlistMode = true;
            buildSetlistScreen();

            Toast.makeText(this, "Playlist criada: " + name, Toast.LENGTH_LONG).show();
            dialog.dismiss();
        }));

        dialog.show();
    }

    private void addPerformancePads(LinearLayout parent) {
        if (!hasSelectedBank()) {
            return;
        }

        Bank bank = banks.get(selectedBankIndex);
        int presetLimit = Math.min(bank.presets.size(), 8);

        for (int i = 0; i < presetLimit; i += 2) {
            boolean hasSecondPreset = i + 1 < presetLimit;

            if (!hasSecondPreset) {
                parent.addView(
                        createPerformancePad(bank.presets.get(i), i),
                        marginParams(-1, dp(92), 0, 0, 0, dp(8))
                );
                continue;
            }

            LinearLayout row = new LinearLayout(this);
            row.setOrientation(LinearLayout.HORIZONTAL);

            row.addView(
                    createPerformancePad(bank.presets.get(i), i),
                    new LinearLayout.LayoutParams(0, dp(92), 1)
            );
            row.addView(
                    createPerformancePad(bank.presets.get(i + 1), i + 1),
                    marginParams(0, dp(92), dp(10), 0, 0, 0, 1)
            );

            parent.addView(row, marginParams(-1, dp(92), 0, 0, 0, dp(8)));
        }
    }

    private LinearLayout createPerformancePad(Preset preset, int index) {
        boolean active = index == activePresetIndex;

        LinearLayout pad = new LinearLayout(this);
        pad.setOrientation(LinearLayout.VERTICAL);
        pad.setGravity(Gravity.CENTER);
        pad.setPadding(dp(8), dp(8), dp(8), dp(8));
        pad.setBackground(createRoundedBackground(
                active ? palette.accent : palette.padBackground,
                active ? palette.accentBright : palette.secondaryAccentDark,
                20,
                active ? 2 : 1
        ));

        TextView presetName = createPrimaryText(preset.name.toUpperCase(), 16, true);
        presetName.setGravity(Gravity.CENTER);
        presetName.setMaxLines(1);

        TextView parts = createSecondaryText(preset.getShortActivePartsSummary(), 10, false);
        parts.setGravity(Gravity.CENTER);
        parts.setPadding(0, dp(4), 0, 0);

        if (active) {
            presetName.setTextColor(palette.textDark);
            parts.setTextColor(palette.textDark);
        }

        pad.addView(presetName);
        pad.addView(parts);

        pad.setOnClickListener(v -> {
            activePresetIndex = index;
            applyPreset(banks.get(selectedBankIndex), preset);
            buildPerformanceScreen();
        });

        return pad;
    }

    private void showReadCurrentSceneConfirmation() {
        new AlertDialog.Builder(this)
                .setTitle("LER CENA ATUAL?")
                .setMessage(
                        "O app vai ler o nome e o MIDI da Scene atualmente "
                                + "carregada no JUNO-D.\n\n"
                                + "Nenhuma Scene, Part, timbre, preset ou "
                                + "configuração do teclado será alterado."
                )
                .setNegativeButton("CANCELAR", null)
                .setPositiveButton("LER", (dialog, which) -> requestCurrentSceneName())
                .show();
    }

    private void requestCurrentSceneName() {
        if (selectedMidiDeviceInfo == null) {
            Toast.makeText(
                    this,
                    "Conecte um dispositivo USB-MIDI antes de ler a Scene.",
                    Toast.LENGTH_LONG
            ).show();
            return;
        }

        if (midiInputPort == null) {
            openMidiDeviceIfNeeded();
            Toast.makeText(
                    this,
                    "MIDI reconectando. Aguarde 1 segundo e tente novamente.",
                    Toast.LENGTH_LONG
            ).show();
            return;
        }

        cancelSceneRead();

        waitingForCurrentSceneName = true;
        pendingCurrentSceneName = null;
        pendingSceneBankMsb = -1;
        pendingSceneBankLsb = -1;
        pendingSceneProgramChange = -1;

        currentSceneNameTimeout = () -> {
            if (!waitingForCurrentSceneName) {
                return;
            }

            waitingForCurrentSceneName = false;

            Toast.makeText(
                    this,
                    "O JUNO-D não respondeu ao pedido de nome da Scene.\n"
                            + "Verifique a conexão USB-MIDI e se Rx Exclusive "
                            + "está ligado no teclado.",
                    Toast.LENGTH_LONG
            ).show();
        };

        mainHandler.postDelayed(currentSceneNameTimeout, 2500);

        byte[] request = buildRolandRq1(
                0x01, 0x00, 0x00, 0x00,
                0x00, 0x00, 0x00, SCENE_NAME_LENGTH
        );

        try {
            midiInputPort.send(request, 0, request.length);
            Toast.makeText(
                    this,
                    "Lendo Scene atual do JUNO-D...",
                    Toast.LENGTH_SHORT
            ).show();
        } catch (IOException exception) {
            waitingForCurrentSceneName = false;
            mainHandler.removeCallbacks(currentSceneNameTimeout);
            currentSceneNameTimeout = null;

            closeMidiDevice();
            openMidiDeviceIfNeeded();

            Toast.makeText(
                    this,
                    "Falha ao consultar o JUNO-D. A conexão MIDI será reaberta.",
                    Toast.LENGTH_LONG
            ).show();
        }
    }

    private void requestCurrentSceneMidi() {
        if (midiInputPort == null) {
            Toast.makeText(
                    this,
                    "A conexão MIDI foi perdida durante a leitura.",
                    Toast.LENGTH_LONG
            ).show();
            return;
        }

        waitingForCurrentSceneMidi = true;

        if (currentSceneMidiTimeout != null) {
            mainHandler.removeCallbacks(currentSceneMidiTimeout);
        }

        currentSceneMidiTimeout = () -> {
            if (!waitingForCurrentSceneMidi) {
                return;
            }

            waitingForCurrentSceneMidi = false;

            Toast.makeText(
                    this,
                    "O JUNO-D respondeu ao nome, mas não respondeu ao MIDI da Scene atual.",
                    Toast.LENGTH_LONG
            ).show();
        };

        mainHandler.postDelayed(currentSceneMidiTimeout, 2500);

        byte[] request = buildRolandRq1(
                0x00, 0x10, 0x00, 0x00,
                0x00, 0x00, 0x00, 0x03
        );

        try {
            midiInputPort.send(request, 0, request.length);
        } catch (IOException exception) {
            waitingForCurrentSceneMidi = false;
            mainHandler.removeCallbacks(currentSceneMidiTimeout);
            currentSceneMidiTimeout = null;

            closeMidiDevice();
            openMidiDeviceIfNeeded();

            Toast.makeText(
                    this,
                    "Falha ao ler o MIDI da Scene. A conexão será reaberta.",
                    Toast.LENGTH_LONG
            ).show();
        }
    }

    private void cancelSceneRead() {
        waitingForCurrentSceneName = false;
        waitingForCurrentSceneMidi = false;

        if (currentSceneNameTimeout != null) {
            mainHandler.removeCallbacks(currentSceneNameTimeout);
            currentSceneNameTimeout = null;
        }

        if (currentSceneMidiTimeout != null) {
            mainHandler.removeCallbacks(currentSceneMidiTimeout);
            currentSceneMidiTimeout = null;
        }
    }

    private byte[] buildRolandRq1(
            int address1,
            int address2,
            int address3,
            int address4,
            int size1,
            int size2,
            int size3,
            int size4
    ) {
        int checksum = calculateRolandChecksum(
                address1,
                address2,
                address3,
                address4,
                size1,
                size2,
                size3,
                size4
        );

        return new byte[] {
                (byte) 0xF0,
                (byte) 0x41,
                (byte) ROLAND_DEVICE_ID,
                (byte) ROLAND_MODEL_ID_1,
                (byte) ROLAND_MODEL_ID_2,
                (byte) ROLAND_MODEL_ID_3,
                (byte) ROLAND_COMMAND_RQ1,
                (byte) address1,
                (byte) address2,
                (byte) address3,
                (byte) address4,
                (byte) size1,
                (byte) size2,
                (byte) size3,
                (byte) size4,
                (byte) checksum,
                (byte) 0xF7
        };
    }

    private int calculateRolandChecksum(int... values) {
        int sum = 0;

        for (int value : values) {
            sum += value & 0x7F;
        }

        return (128 - (sum % 128)) & 0x7F;
    }

    private void refreshMidiDevices() {
        if (midiManager == null) {
            return;
        }

        MidiDeviceInfo[] devices = midiManager.getDevices();

        if (devices == null || devices.length == 0) {
            selectedMidiDeviceInfo = null;
            updateMidiStatus("Nenhum dispositivo MIDI USB detectado.", false);
            return;
        }

        selectedMidiDeviceInfo = devices[0];
        updateMidiStatus(getMidiDeviceName(selectedMidiDeviceInfo) + "\nMIDI detectado.", true);
    }

    private void updateMidiStatus(String text, boolean connected) {
        if (tvMidiStatus != null) {
            tvMidiStatus.setText(text);
        }

        if (tvMidiStatusDot != null) {
            tvMidiStatusDot.setTextColor(connected ? palette.accent : palette.textSecondary);
        }
    }

    private void openMidiDeviceIfNeeded() {
        if (midiInputPort != null || midiOutputPort != null || midiDevice != null) {
            return;
        }

        if (midiManager == null || selectedMidiDeviceInfo == null) {
            return;
        }

        midiManager.openDevice(selectedMidiDeviceInfo, device -> {
            if (device == null) {
                runOnUiThread(() -> updateMidiStatus("Não foi possível abrir o dispositivo MIDI.", false));
                return;
            }

            midiDevice = device;

            if (selectedMidiDeviceInfo.getOutputPortCount() > 0) {
                midiInputPort = device.openInputPort(0);
            }

            if (selectedMidiDeviceInfo.getInputPortCount() > 0) {
                midiOutputPort = device.openOutputPort(0);

                if (midiOutputPort != null) {
                    midiReceiver = new MidiReceiver() {
                        @Override
                        public void onSend(byte[] message, int offset, int count, long timestamp) {
                            byte[] received = new byte[count];
                            System.arraycopy(message, offset, received, 0, count);
                            handleIncomingMidiMessage(received);
                        }
                    };

                    midiOutputPort.connect(midiReceiver);
                }
            }

            runOnUiThread(() -> {
                if (midiInputPort != null) {
                    updateMidiStatus(getMidiDeviceName(selectedMidiDeviceInfo)
                            + "\nMIDI conectado.", true);
                } else {
                    updateMidiStatus("Dispositivo MIDI sem porta de envio disponível.", false);
                }
            });
        }, null);
    }

    private void handleIncomingMidiMessage(byte[] message) {
        if (waitingForCurrentSceneName && isCurrentSceneNameDt1(message)) {
            pendingCurrentSceneName = decodeCurrentSceneName(message);
            waitingForCurrentSceneName = false;

            if (currentSceneNameTimeout != null) {
                mainHandler.removeCallbacks(currentSceneNameTimeout);
                currentSceneNameTimeout = null;
            }

            mainHandler.postDelayed(this::requestCurrentSceneMidi, 40);
            return;
        }

        if (waitingForCurrentSceneMidi && isCurrentSceneMidiDt1(message)) {
            pendingSceneBankMsb = message[11] & 0x7F;
            pendingSceneBankLsb = message[12] & 0x7F;
            pendingSceneProgramChange = message[13] & 0x7F;
            waitingForCurrentSceneMidi = false;

            if (currentSceneMidiTimeout != null) {
                mainHandler.removeCallbacks(currentSceneMidiTimeout);
                currentSceneMidiTimeout = null;
            }

            runOnUiThread(() -> showCurrentSceneResult(
                    pendingCurrentSceneName,
                    pendingSceneBankMsb,
                    pendingSceneBankLsb,
                    pendingSceneProgramChange
            ));
        }
    }

    private boolean isRolandDt1(byte[] message) {
        return message != null
                && message.length >= 14
                && (message[0] & 0xFF) == 0xF0
                && (message[1] & 0xFF) == 0x41
                && (message[3] & 0xFF) == ROLAND_MODEL_ID_1
                && (message[4] & 0xFF) == ROLAND_MODEL_ID_2
                && (message[5] & 0xFF) == ROLAND_MODEL_ID_3
                && (message[6] & 0xFF) == ROLAND_COMMAND_DT1
                && (message[message.length - 1] & 0xFF) == 0xF7;
    }

    private boolean isCurrentSceneNameDt1(byte[] message) {
        return isRolandDt1(message)
                && message.length >= 29
                && (message[7] & 0xFF) == 0x01
                && (message[8] & 0xFF) == 0x00
                && (message[9] & 0xFF) == 0x00
                && (message[10] & 0xFF) == 0x00;
    }

    private boolean isCurrentSceneMidiDt1(byte[] message) {
        return isRolandDt1(message)
                && message.length >= 16
                && (message[7] & 0xFF) == 0x00
                && (message[8] & 0xFF) == 0x10
                && (message[9] & 0xFF) == 0x00
                && (message[10] & 0xFF) == 0x00;
    }

    private String decodeCurrentSceneName(byte[] message) {
        StringBuilder name = new StringBuilder();

        for (int i = 0; i < SCENE_NAME_LENGTH; i++) {
            int value = message[11 + i] & 0x7F;

            if (value >= 0x20 && value <= 0x7E) {
                name.append((char) value);
            } else {
                name.append(' ');
            }
        }

        String result = name.toString().trim();
        return result.isEmpty() ? "Scene sem nome" : result;
    }

    private void showCurrentSceneResult(
            String sceneName,
            int bankMsb,
            int bankLsb,
            int programChange
    ) {
        boolean isUserScene = bankMsb == USER_SCENE_BANK_MSB
                && bankLsb == USER_SCENE_BANK_LSB;

        String midiDescription = isUserScene
                ? "USER " + (programChange + 1)
                : "MSB " + bankMsb
                + " / LSB " + bankLsb
                + " / Program " + (programChange + 1);

        new AlertDialog.Builder(this)
                .setTitle("CENA ATUAL DO JUNO-D")
                .setMessage(
                        "Nome lido do teclado:\n\n"
                                + sceneName
                                + "\n\n"
                                + "MIDI detectado:\n"
                                + midiDescription
                                + "\n\n"
                                + (isUserScene
                                ? "Você pode adicionar esta Scene ao app. Os presets começarão vazios."
                                : "A Scene atual não pertence ao banco USER. Você ainda pode adicioná-la, mas o campo MIDI será iniciado em 1 para revisão.")
                )
                .setNegativeButton("CANCELAR", null)
                .setPositiveButton(
                        "ADICIONAR AO APP",
                        (dialog, which) -> showAddReadSceneDialog(
                                sceneName,
                                isUserScene ? programChange + 1 : 1
                        )
                )
                .show();
    }

    private void showAddReadSceneDialog(String keyboardSceneName, int detectedSceneNumber) {
        LinearLayout form = new LinearLayout(this);
        form.setOrientation(LinearLayout.VERTICAL);
        form.setPadding(dp(18), dp(8), dp(18), dp(4));

        TextView information = createSecondaryText(
                "O nome e o MIDI foram lidos da Scene atualmente carregada no JUNO-D. "
                        + "Você pode editar os dois campos antes de salvar.",
                12,
                false
        );
        form.addView(information, marginParams(-1, -2, 0, 0, 0, dp(14)));

        form.addView(createDialogLabel("NOME DA CENA"));

        EditText inputName = createThemeEditText("Nome da cena");
        inputName.setText(keyboardSceneName);
        inputName.setSelectAllOnFocus(false);
        form.addView(inputName);

        TextView sceneLabel = createDialogLabel("USER SCENE MIDI (1 A 128)");
        sceneLabel.setPadding(0, dp(16), 0, dp(6));
        form.addView(sceneLabel);

        EditText inputScene = createThemeEditText("Ex.: 12");
        inputScene.setInputType(InputType.TYPE_CLASS_NUMBER);
        inputScene.setText(String.valueOf(detectedSceneNumber));
        form.addView(inputScene);

        AlertDialog dialog = new AlertDialog.Builder(this)
                .setTitle("ADICIONAR CENA AO APP")
                .setView(form)
                .setNegativeButton("CANCELAR", null)
                .setPositiveButton("SALVAR", null)
                .create();

        dialog.setOnShowListener(listener -> {
            Button saveButton = dialog.getButton(AlertDialog.BUTTON_POSITIVE);

            saveButton.setOnClickListener(view -> {
                String name = inputName.getText().toString().trim();
                int scene = parseSceneNumber(inputScene.getText().toString());

                if (name.isEmpty()) {
                    inputName.setError("Informe um nome.");
                    inputName.requestFocus();
                    return;
                }

                if (scene < 1 || scene > 128) {
                    inputScene.setError("Informe um número entre 1 e 128.");
                    inputScene.requestFocus();
                    return;
                }

                Bank bank = new Bank(
                        UUID.randomUUID().toString(),
                        name,
                        scene
                );
                banks.add(bank);
                sortBanksAlphabetically();

                selectedBankIndex = banks.indexOf(bank);
                activePresetIndex = -1;

                saveBanks();
                configureBankSpinner();

                if (spinnerBanks != null && selectedBankIndex >= 0) {
                    spinnerBanks.setSelection(selectedBankIndex);
                }

                Toast.makeText(
                        this,
                        "Cena adicionada ao app: " + name,
                        Toast.LENGTH_LONG
                ).show();

                dialog.dismiss();
            });
        });

        dialog.show();
    }

    private void showCreateBankDialog() {
        LinearLayout form = new LinearLayout(this);
        form.setOrientation(LinearLayout.VERTICAL);
        form.setPadding(dp(18), dp(8), dp(18), dp(4));

        form.addView(createDialogLabel("NOME DA CENA"));
        EditText inputName = createThemeEditText("Ex.: Enjoy the Silence");
        form.addView(inputName);

        TextView label = createDialogLabel("USER SCENE (1 A 128)");
        label.setPadding(0, dp(16), 0, dp(6));
        form.addView(label);

        EditText inputScene = createThemeEditText("Ex.: 12");
        inputScene.setInputType(InputType.TYPE_CLASS_NUMBER);
        inputScene.setText("1");
        form.addView(inputScene);

        AlertDialog dialog = new AlertDialog.Builder(this)
                .setTitle("NOVA CENA")
                .setView(form)
                .setNegativeButton("CANCELAR", null)
                .setPositiveButton("CRIAR", null)
                .create();

        dialog.setOnShowListener(v -> dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(x -> {
            String name = inputName.getText().toString().trim();
            int scene = parseSceneNumber(inputScene.getText().toString());

            if (name.isEmpty()) {
                inputName.setError("Informe um nome.");
                inputName.requestFocus();
                return;
            }

            if (scene < 1 || scene > 128) {
                inputScene.setError("Informe um número entre 1 e 128.");
                inputScene.requestFocus();
                return;
            }

            Bank bank = new Bank(
                    UUID.randomUUID().toString(),
                    name,
                    scene
            );
            banks.add(bank);
            sortBanksAlphabetically();

            selectedBankIndex = banks.indexOf(bank);
            activePresetIndex = -1;

            saveBanks();
            configureBankSpinner();

            if (spinnerBanks != null && selectedBankIndex >= 0) {
                spinnerBanks.setSelection(selectedBankIndex);
            }

            dialog.dismiss();
        }));

        dialog.show();
    }

    private void showDeleteBankDialog() {
        if (!hasSelectedBank()) {
            return;
        }

        new AlertDialog.Builder(this)
                .setTitle("EXCLUIR CENA?")
                .setMessage("A cena e seus presets serão apagados.")
                .setNegativeButton("CANCELAR", null)
                .setPositiveButton("EXCLUIR", (dialog, which) -> {
                    banks.remove(selectedBankIndex);
                    selectedBankIndex = -1;
                    activePresetIndex = -1;
                    saveBanks();
                    configureBankSpinner();
                })
                .show();
    }

    private void showDeletePresetDialog(Preset preset, int presetIndex) {
        if (!hasSelectedBank()) {
            return;
        }

        AlertDialog dialog = new AlertDialog.Builder(this)
                .setTitle("EXCLUIR PRESET?")
                .setMessage("O preset \"" + preset.name + "\" será apagado desta cena.")
                .setNegativeButton("CANCELAR", null)
                .setPositiveButton("EXCLUIR", null)
                .create();

        dialog.setOnShowListener(listener -> {
            Button deleteButton = dialog.getButton(AlertDialog.BUTTON_POSITIVE);

            if (deleteButton != null) {
                deleteButton.setTextColor(palette.accent);
                deleteButton.setOnClickListener(view -> {
                    Bank bank = banks.get(selectedBankIndex);

                    if (presetIndex >= 0 && presetIndex < bank.presets.size()) {
                        bank.presets.remove(presetIndex);
                        saveBanks();
                        refreshPresetList();

                        Toast.makeText(this, "Preset excluído.", Toast.LENGTH_SHORT).show();
                    }

                    dialog.dismiss();
                });
            }
        });

        dialog.show();
    }

    private void showPresetEditor(Preset preset, int index) {
        boolean editing = preset != null;

        LinearLayout form = new LinearLayout(this);
        form.setOrientation(LinearLayout.VERTICAL);
        form.setPadding(dp(18), dp(8), dp(18), dp(14));

        EditText inputName = createThemeEditText("Nome do preset");
        if (editing) {
            inputName.setText(preset.name);
        }

        form.addView(createDialogLabel("NOME DO PRESET"));
        form.addView(inputName);
        form.addView(
                createDialogLabel("PARTS ATIVAS"),
                marginParams(-1, -2, 0, dp(16), 0, dp(4))
        );

        boolean[] states = new boolean[8];
        if (editing) {
            System.arraycopy(preset.partStates, 0, states, 0, 8);
        } else {
            states[0] = true;
        }

        LinearLayout grid = new LinearLayout(this);
        grid.setOrientation(LinearLayout.VERTICAL);

        for (int row = 0; row < 4; row++) {
            LinearLayout line = new LinearLayout(this);

            for (int col = 0; col < 2; col++) {
                int part = row * 2 + col;
                Button button = createPartButton(part, states[part]);
                final int partIndex = part;

                button.setOnClickListener(v -> {
                    states[partIndex] = !states[partIndex];
                    updatePartButton(button, partIndex, states[partIndex]);
                });

                line.addView(
                        button,
                        marginParams(
                                0,
                                dp(56),
                                col == 1 ? dp(8) : 0,
                                dp(4),
                                0,
                                dp(4),
                                1
                        )
                );
            }

            grid.addView(line);
        }

        form.addView(grid);

        AlertDialog dialog = new AlertDialog.Builder(this)
                .setTitle(editing ? "EDITAR PRESET" : "NOVO PRESET")
                .setView(form)
                .setNegativeButton("CANCELAR", null)
                .setPositiveButton("SALVAR", null)
                .create();

        dialog.setOnShowListener(v -> dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(x -> {
            String name = inputName.getText().toString().trim();

            if (name.isEmpty()) {
                inputName.setError("Informe um nome.");
                inputName.requestFocus();
                return;
            }

            if (!hasSelectedBank()) {
                return;
            }

            if (editing) {
                preset.name = name;
                System.arraycopy(states, 0, preset.partStates, 0, 8);
            } else {
                banks.get(selectedBankIndex).presets.add(new Preset(name, 1, states));
            }

            saveBanks();
            refreshPresetList();
            dialog.dismiss();
        }));

        dialog.show();
    }

    private void closeMidiDevice() {
        if (midiOutputPort != null) {
            if (midiReceiver != null) {
                midiOutputPort.disconnect(midiReceiver);
            }

            try {
                midiOutputPort.close();
            } catch (IOException ignored) {
            }

            midiOutputPort = null;
            midiReceiver = null;
        }

        if (midiInputPort != null) {
            try {
                midiInputPort.close();
            } catch (IOException ignored) {
            }

            midiInputPort = null;
        }

        if (midiDevice != null) {
            try {
                midiDevice.close();
            } catch (IOException ignored) {
            }

            midiDevice = null;
        }
    }

    private void sendUserScene(int sceneNumber) {
        if (sceneNumber < 1 || sceneNumber > 128) {
            Toast.makeText(this, "Cena inválida no app: " + sceneNumber, Toast.LENGTH_LONG).show();
            return;
        }

        if (selectedMidiDeviceInfo == null) {
            Toast.makeText(this, "MIDI: nenhum dispositivo selecionado.", Toast.LENGTH_LONG).show();
            return;
        }

        if (midiInputPort == null) {
            openMidiDeviceIfNeeded();
            Toast.makeText(
                    this,
                    "MIDI reconectando. Troque a cena novamente em 1 segundo.",
                    Toast.LENGTH_LONG
            ).show();
            return;
        }

        int program = sceneNumber - 1;

        byte[] messages = new byte[] {
                (byte) (0xB0 | MIDI_CHANNEL),
                (byte) 0,
                (byte) USER_SCENE_BANK_MSB,
                (byte) (0xB0 | MIDI_CHANNEL),
                (byte) 32,
                (byte) USER_SCENE_BANK_LSB,
                (byte) (0xC0 | MIDI_CHANNEL),
                (byte) program
        };

        try {
            midiInputPort.send(messages, 0, messages.length);

            Toast.makeText(
                    this,
                    "Enviado USER " + sceneNumber + " para " + getMidiDeviceName(selectedMidiDeviceInfo),
                    Toast.LENGTH_LONG
            ).show();
        } catch (IOException exception) {
            closeMidiDevice();
            openMidiDeviceIfNeeded();

            Toast.makeText(
                    this,
                    "Conexão MIDI caiu e está sendo reaberta. Troque a cena novamente em 1 segundo.",
                    Toast.LENGTH_LONG
            ).show();
        }
    }

    private void selectSongScene(Bank bank) {
        if (bank != null) {
            sendUserScene(bank.scene);
        }
    }

    private String getMidiDeviceName(MidiDeviceInfo info) {
        if (info == null) {
            return "Dispositivo desconhecido";
        }

        String name = info.getProperties().getString(MidiDeviceInfo.PROPERTY_NAME);
        return name == null || name.trim().isEmpty()
                ? "Dispositivo MIDI #" + info.getId()
                : name;
    }

    private void configureBankSpinner() {
        if (spinnerBanks == null) {
            return;
        }

        List<String> names = new ArrayList<>();
        for (Bank bank : banks) {
            names.add(bank.name);
        }

        if (names.isEmpty()) {
            names.add("Nenhuma cena criada");
        }

        ArrayAdapter<String> adapter = new ArrayAdapter<String>(
            this,
            android.R.layout.simple_spinner_item,
            names
        ) {
            @Override
            public View getView(
                int position,
                View convertView,
                android.view.ViewGroup parent
            ) {
                TextView textView = (TextView) super.getView(
                    position,
                    convertView,
                    parent
                );

                boolean darkTheme =
                    selectedTheme == THEME_STANDARD_DARK;

                // Spinner fechado.
                textView.setTextColor(
                    darkTheme ? Color.WHITE : Color.BLACK
                );
                textView.setTextSize(16);
                textView.setTypeface(Typeface.DEFAULT_BOLD);
                textView.setGravity(Gravity.CENTER);
                textView.setPadding(dp(12), 0, dp(12), 0);

                return textView;
            }

            @Override
            public View getDropDownView(
                int position,
                View convertView,
                android.view.ViewGroup parent
            ) {
                TextView textView = (TextView) super.getDropDownView(
                    position,
                    convertView,
                    parent
                );

                boolean darkTheme =
                    selectedTheme == THEME_STANDARD_DARK;

                // Itens da lista aberta.
                textView.setTextColor(
                    darkTheme ? Color.WHITE : Color.BLACK
                );
                textView.setTextSize(16);
                textView.setTypeface(Typeface.DEFAULT_BOLD);
                textView.setGravity(Gravity.CENTER);
                textView.setPadding(dp(16), dp(12), dp(16), dp(12));

                textView.setBackgroundColor(
                    darkTheme
                        ? Color.rgb(18, 18, 18)
                        : Color.WHITE
                );

                return textView;
            }
        };

        spinnerBanks.setAdapter(adapter);

        spinnerBanks.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                selectedBankIndex = banks.isEmpty() ? -1 : position;

                if (hasSelectedBank()) {
                    selectSongScene(banks.get(selectedBankIndex));
                }

                refreshPresetList();
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {
                selectedBankIndex = -1;
                refreshPresetList();
            }
        });

        if (banks.isEmpty()) {
            selectedBankIndex = -1;
            spinnerBanks.setEnabled(false);
        } else {
            spinnerBanks.setEnabled(true);

            if (!hasSelectedBank()) {
                selectedBankIndex = 0;
            }

            spinnerBanks.setSelection(selectedBankIndex);
        }

        refreshPresetList();
    }

    private void refreshPresetList() {
        if (layoutPresets == null) {
            return;
        }

        layoutPresets.removeAllViews();

        boolean selected = hasSelectedBank();
        btnNewPreset.setEnabled(selected);
        btnDeleteBank.setEnabled(selected);

        if (!selected) {
            tvEmptyPresets.setText("Crie uma cena para começar.");
            tvEmptyPresets.setVisibility(View.VISIBLE);
            return;
        }

        Bank bank = banks.get(selectedBankIndex);

        if (bank.presets.isEmpty()) {
            tvEmptyPresets.setText("NENHUM PRESET CRIADO");
            tvEmptyPresets.setVisibility(View.VISIBLE);
            return;
        }

        tvEmptyPresets.setVisibility(View.GONE);

        for (int i = 0; i < bank.presets.size(); i++) {
            addPresetCard(bank, bank.presets.get(i), i);
        }
    }

    private void addPresetCard(Bank bank, Preset preset, int index) {
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(dp(14), dp(14), dp(14), dp(12));
        card.setBackground(createPanelBackground(palette.panelBackground, palette.border, 18));
        card.setClickable(true);
        card.setOnClickListener(v -> showPresetEditor(preset, index));

        TextView name = createPrimaryText(preset.name, 19, true);
        card.addView(name);
        card.addView(
                createSecondaryText(preset.getActivePartsSummary(), 13, false),
                marginParams(-1, -2, 0, dp(8), 0, dp(12))
        );

        LinearLayout actionRow = new LinearLayout(this);
        actionRow.setOrientation(LinearLayout.HORIZONTAL);

        Button apply = createPrimaryActionButton("APLICAR");
        apply.setOnClickListener(v -> applyPreset(bank, preset));

        Button delete = createOutlineButton("EXCLUIR");
        delete.setOnClickListener(v -> showDeletePresetDialog(preset, index));

        actionRow.addView(apply, new LinearLayout.LayoutParams(0, dp(32), 1));
        actionRow.addView(delete, marginParams(0, dp(32), dp(8), 0, 0, 0, 1));

        card.addView(actionRow);
        layoutPresets.addView(card, marginParams(-1, -2, 0, 0, 0, dp(10)));
    }

    private void applyPreset(Bank bank, Preset preset) {
        if (midiInputPort == null) {
            openMidiDeviceIfNeeded();

            Toast.makeText(
                    this,
                    "MIDI reconectando. Toque em APLICAR novamente em 1 segundo.",
                    Toast.LENGTH_LONG
            ).show();
            return;
        }

        try {
            for (int partIndex = 0; partIndex < 8; partIndex++) {
                int partNumber = partIndex + 1;
                int addressPart = 0x20 + partNumber;
                int value = preset.partStates[partIndex] ? 0x01 : 0x00;
                int checksum = calculateRolandChecksum(
                        0x01,
                        0x00,
                        addressPart,
                        0x00,
                        value
                );

                byte[] message = new byte[] {
                        (byte) 0xF0,
                        (byte) 0x41,
                        (byte) ROLAND_DEVICE_ID,
                        (byte) ROLAND_MODEL_ID_1,
                        (byte) ROLAND_MODEL_ID_2,
                        (byte) ROLAND_MODEL_ID_3,
                        (byte) ROLAND_COMMAND_DT1,
                        (byte) 0x01,
                        (byte) 0x00,
                        (byte) addressPart,
                        (byte) 0x00,
                        (byte) value,
                        (byte) checksum,
                        (byte) 0xF7
                };

                midiInputPort.send(message, 0, message.length);
            }

            Toast.makeText(
                    this,
                    "PRESET APLICADO\n"
                            + preset.name.toUpperCase()
                            + "\n\n"
                            + preset.getActivePartsSummary(),
                    Toast.LENGTH_LONG
            ).show();
        } catch (IOException exception) {
            closeMidiDevice();
            openMidiDeviceIfNeeded();

            Toast.makeText(
                    this,
                    "MIDI caiu. Reconectando; toque novamente em 1 segundo.",
                    Toast.LENGTH_LONG
            ).show();
        }
    }

    private void saveBanks() {
        JSONArray array = new JSONArray();

        try {
            for (Bank bank : banks) {
                JSONObject object = new JSONObject();
                object.put("id", bank.id);
                object.put("name", bank.name);
                object.put("scene", bank.scene);

                JSONArray presets = new JSONArray();

                for (Preset preset : bank.presets) {
                    JSONObject presetObject = new JSONObject();
                    presetObject.put("name", preset.name);
                    presetObject.put("scene", preset.scene);

                    JSONArray states = new JSONArray();
                    for (boolean state : preset.partStates) {
                        states.put(state);
                    }

                    presetObject.put("partStates", states);
                    presets.put(presetObject);
                }

                object.put("presets", presets);
                array.put(object);
            }
        } catch (JSONException ignored) {
            return;
        }

        getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                .edit()
                .putString(KEY_BANKS_JSON, array.toString())
                .apply();
    }

    private void loadBanks() {
        banks.clear();

        String saved = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                .getString(KEY_BANKS_JSON, null);

        if (saved == null || saved.trim().isEmpty()) {
            return;
        }

        try {
            JSONArray array = new JSONArray(saved);

            for (int i = 0; i < array.length(); i++) {
                JSONObject object = array.getJSONObject(i);
                int scene = object.optInt("scene", 1);

                Bank bank = new Bank(
                        object.optString("id", UUID.randomUUID().toString()),
                        object.optString("name", "Cena sem nome"),
                        scene
                );

                JSONArray presets = object.optJSONArray("presets");

                if (presets != null) {
                    for (int j = 0; j < presets.length(); j++) {
                        JSONObject presetObject = presets.getJSONObject(j);
                        boolean[] states = new boolean[8];
                        JSONArray values = presetObject.optJSONArray("partStates");

                        if (values != null) {
                            for (int k = 0; k < 8 && k < values.length(); k++) {
                                states[k] = values.optBoolean(k, false);
                            }
                        }

                        bank.presets.add(new Preset(
                                presetObject.optString("name", "Preset sem nome"),
                                presetObject.optInt("scene", 1),
                                states
                        ));
                    }
                }

                banks.add(bank);
            }

            sortBanksAlphabetically();
        } catch (JSONException ignored) {
            banks.clear();
        }
    }

    private String saveSetlistsToJson() {
        JSONArray array = new JSONArray();

        try {
            for (Setlist setlist : setlists) {
                JSONObject object = new JSONObject();
                object.put("name", setlist.name);

                JSONArray musicOrder = new JSONArray();
                for (int index : setlist.bankIndices) {
                    if (index >= 0 && index < banks.size()) {
                        musicOrder.put(banks.get(index).id);
                    }
                }

                object.put("musicOrder", musicOrder);
                array.put(object);
            }
        } catch (JSONException ignored) {
            return null;
        }

        return array.toString();
    }

    private void saveSetlists() {
        String json = saveSetlistsToJson();
        if (json == null) return;

        getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                .edit()
                .putString(KEY_SETLISTS_JSON, json)
                .apply();
    }

    private void loadSetlists() {
        setlists.clear();

        String saved = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                .getString(KEY_SETLISTS_JSON, null);

        if (saved == null || saved.trim().isEmpty()) {
            return;
        }

        try {
            JSONArray array = new JSONArray(saved);

            for (int i = 0; i < array.length(); i++) {
                JSONObject object = array.getJSONObject(i);
                String name = object.optString("name", "Playlist sem nome");

                Setlist setlist = new Setlist(name);

                JSONArray musicOrder = object.optJSONArray("musicOrder");
                if (musicOrder != null) {
                    for (int j = 0; j < musicOrder.length(); j++) {
                        String bankId = musicOrder.optString(j, null);
                        if (bankId != null) {
                            for (Bank bank : banks) {
                                if (bankId.equals(bank.id)) {
                                    int index = banks.indexOf(bank);
                                    setlist.addBank(index);
                                    break;
                                }
                            }
                        }
                    }
                }

                setlists.add(setlist);
            }
        } catch (JSONException ignored) {
            setlists.clear();
        }
    }

    private TextView createSectionTitle(String text) {
        return createSecondaryText(text, 12, true);
    }

    private TextView createDialogLabel(String text) {
        return createSecondaryText(text, 11, true);
    }

    private TextView createPrimaryText(String text, float size, boolean bold) {
        TextView view = new TextView(this);
        view.setText(text);
        view.setTextSize(size);
        view.setTextColor(palette.textPrimary);

        if (bold) {
            view.setTypeface(Typeface.DEFAULT_BOLD);
        }

        return view;
    }

    private TextView createSecondaryText(String text, float size, boolean bold) {
        TextView view = new TextView(this);
        view.setText(text);
        view.setTextSize(size);
        view.setTextColor(palette.textSecondary);

        if (bold) {
            view.setTypeface(Typeface.DEFAULT_BOLD);
        }

        return view;
    }

    private TextView createAccentText(String text, float size, boolean bold) {
        TextView view = new TextView(this);
        view.setText(text);
        view.setTextSize(size);
        view.setTextColor(palette.accent);

        if (bold) {
            view.setTypeface(Typeface.DEFAULT_BOLD);
        }

        return view;
    }

    private Button createPrimaryActionButton(String text) {
        Button button = new Button(this);
        button.setText(text);
        button.setAllCaps(false);
        button.setTextSize(12);
        button.setTypeface(Typeface.DEFAULT_BOLD);
        button.setTextColor(palette.textDark);
        button.setGravity(Gravity.CENTER);
        button.setLetterSpacing(0.025f);
        button.setPadding(dp(10), 0, dp(10), 0);
        button.setBackground(createRaisedBackground(
                palette.accent,
                palette.accentBright,
                palette.accentDark,
                18
        ));
        applyDarkTextShadow(button);
        applyButtonElevation(button, 5);
        return button;
    }

    private Button createOutlineButton(String text) {
        Button button = new Button(this);
        button.setText(text);
        button.setAllCaps(false);
        button.setTextSize(12);
        button.setTypeface(Typeface.DEFAULT_BOLD);
        button.setTextColor(palette.secondaryAccent);
        button.setGravity(Gravity.CENTER);
        button.setLetterSpacing(0.025f);
        button.setPadding(dp(10), 0, dp(10), 0);
        button.setBackground(createDarkRaisedBackground(
                palette.panelStrong,
                palette.secondaryAccentDark,
                palette.secondaryAccent,
                18
        ));
        applyGlow(button, palette.secondaryAccent, 1.6f, 120);
        applyButtonElevation(button, 3);
        return button;
    }

    private EditText createThemeEditText(String hint) {
        EditText editText = new EditText(this);
        editText.setHint(hint);
        editText.setTextColor(palette.textPrimary);
        editText.setHintTextColor(palette.textSecondary);
        editText.setSingleLine(true);
        editText.setBackground(createRoundedBackground(
                palette.panelStrong,
                palette.secondaryAccentDark,
                12,
                1
        ));
        return editText;
    }

    private Button createPartButton(int index, boolean enabled) {
        Button button = new Button(this);
        button.setAllCaps(false);
        updatePartButton(button, index, enabled);
        return button;
    }

    private void updatePartButton(Button button, int index, boolean enabled) {
        button.setText("PART " + (index + 1) + "\n" + (enabled ? "ON" : "OFF"));
        button.setTextSize(12);
        button.setTypeface(Typeface.DEFAULT_BOLD);
        button.setGravity(Gravity.CENTER);
        button.setPadding(dp(4), 0, dp(4), 0);

        if (enabled) {
            button.setTextColor(palette.textDark);
            button.setBackground(createRaisedBackground(
                    palette.accent,
                    palette.accentBright,
                    palette.accentDark,
                    16
            ));
            applyDarkTextShadow(button);
            applyButtonElevation(button, 3);
        } else {
            button.setTextColor(palette.secondaryAccent);
            button.setBackground(createDarkRaisedBackground(
                    palette.partOff,
                    palette.secondaryAccentDark,
                    palette.secondaryAccent,
                    16
            ));
            applyGlow(button, palette.secondaryAccent, 1.2f, 110);
            applyButtonElevation(button, 2);
        }
    }

    private int parseSceneNumber(String value) {
        try {
            return Integer.parseInt(value.trim());
        } catch (NumberFormatException ignored) {
            return 0;
        }
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }

    private float dpFloat(float value) {
        return value * getResources().getDisplayMetrics().density;
    }

    private LinearLayout.LayoutParams marginParams(
            int width,
            int height,
            int left,
            int top,
            int right,
            int bottom
    ) {
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(width, height);
        params.setMargins(left, top, right, bottom);
        return params;
    }

    private LinearLayout.LayoutParams marginParams(
            int width,
            int height,
            int left,
            int top,
            int right,
            int bottom,
            float weight
    ) {
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(width, height, weight);
        params.setMargins(left, top, right, bottom);
        return params;
    }

    private int withAlpha(int color, int alpha) {
        return Color.argb(
                alpha,
                Color.red(color),
                Color.green(color),
                Color.blue(color)
        );
    }

    private GradientDrawable createRaisedBackground(
            int centerColor,
            int topColor,
            int bottomColor,
            int radiusDp
    ) {
        GradientDrawable drawable = new GradientDrawable(
                GradientDrawable.Orientation.TOP_BOTTOM,
                new int[] {
                        withAlpha(topColor, 165),
                        withAlpha(centerColor, 165),
                        withAlpha(bottomColor, 165)
                }
        );

        drawable.setCornerRadius(dp(radiusDp));
        drawable.setStroke(dp(1), topColor);
        return drawable;
    }

    private GradientDrawable createDarkRaisedBackground(
            int centerColor,
            int borderColor,
            int topHighlightColor,
            int radiusDp
    ) {
        int upperColor = Color.argb(
                165,
                Color.red(centerColor),
                Color.green(centerColor),
                Color.blue(centerColor)
        );

        int lowerColor = Color.argb(
                175,
                Math.max(0, Color.red(centerColor) - 8),
                Math.max(0, Color.green(centerColor) - 8),
                Math.max(0, Color.blue(centerColor) - 8)
        );

        GradientDrawable drawable = new GradientDrawable(
                GradientDrawable.Orientation.TOP_BOTTOM,
                new int[] {upperColor, lowerColor}
        );

        drawable.setCornerRadius(dp(radiusDp));
        drawable.setStroke(dp(1), borderColor);
        return drawable;
    }

    private GradientDrawable createPanelBackground(
        int fill,
        int stroke,
        int radius
    ) {
        GradientDrawable drawable = new GradientDrawable();

        int transparentFill = Color.argb(
            175,
            Color.red(fill),
            Color.green(fill),
            Color.blue(fill)
        );

        drawable.setColor(transparentFill);
        drawable.setCornerRadius(dp(radius));
        drawable.setStroke(dp(1), stroke);

        return drawable;
    }

    private GradientDrawable createRoundedBackground(int fill, int stroke, int radius, int width) {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setColor(fill);
        drawable.setCornerRadius(dp(radius));

        if (width > 0) {
            drawable.setStroke(dp(width), stroke);
        }

        return drawable;
    }

    private void applyButtonElevation(View view, int elevationDp) {
        view.setElevation(dp(elevationDp));
    }

    private void applyGlow(TextView view, int color, float radiusDp, int alpha) {
        view.setLayerType(View.LAYER_TYPE_SOFTWARE, null);
        view.setShadowLayer(
                dpFloat(radiusDp),
                0f,
                dpFloat(1f),
                Color.argb(alpha, Color.red(color), Color.green(color), Color.blue(color))
        );
    }

    private void applyDarkTextShadow(TextView view) {
        view.setLayerType(View.LAYER_TYPE_SOFTWARE, null);
        view.setShadowLayer(
                dpFloat(1f),
                0f,
                dpFloat(1f),
                Color.argb(150, 0, 0, 0)
        );
    }

    private static class ThemePalette {
        final int backgroundImageResId;
        final int background;
        final int screenOverlay;
        final int panelBackground;
        final int panelStrong;
        final int padBackground;
        final int accent;
        final int accentBright;
        final int accentDark;
        final int textPrimary;
        final int textSecondary;
        final int textDark;
        final int border;
        final int partOff;
        final int secondaryAccent;
        final int secondaryAccentDark;
        final int activeSecondaryText;

        ThemePalette(
                int image,
                int background,
                int screenOverlay,
                int panelBackground,
                int panelStrong,
                int padBackground,
                int accent,
                int accentBright,
                int accentDark,
                int textPrimary,
                int textSecondary,
                int textDark,
                int border,
                int partOff,
                int secondaryAccent,
                int secondaryAccentDark,
                int activeSecondaryText
        ) {
            this.backgroundImageResId = image;
            this.background = background;
            this.screenOverlay = screenOverlay;
            this.panelBackground = panelBackground;
            this.panelStrong = panelStrong;
            this.padBackground = padBackground;
            this.accent = accent;
            this.accentBright = accentBright;
            this.accentDark = accentDark;
            this.textPrimary = textPrimary;
            this.textSecondary = textSecondary;
            this.textDark = textDark;
            this.border = border;
            this.partOff = partOff;
            this.secondaryAccent = secondaryAccent;
            this.secondaryAccentDark = secondaryAccentDark;
            this.activeSecondaryText = activeSecondaryText;
        }
    }

    private static class Bank {
        final String id;
        final String name;
        final int scene;
        final List<Preset> presets = new ArrayList<>();

        Bank(String id, String name, int scene) {
            this.id = id;
            this.name = name;
            this.scene = scene;
        }
    }

    private static class Preset {
        String name;
        final int scene;
        final boolean[] partStates;

        Preset(String name, int scene, boolean[] states) {
            this.name = name;
            this.scene = scene;
            this.partStates = new boolean[8];
            System.arraycopy(states, 0, partStates, 0, Math.min(8, states.length));
        }

        String getActivePartsSummary() {
            StringBuilder summary = new StringBuilder("PARTS: ");
            boolean any = false;

            for (int i = 0; i < 8; i++) {
                if (partStates[i]) {
                    if (any) {
                        summary.append(", ");
                    }
                    summary.append(i + 1);
                    any = true;
                }
            }

            if (!any) {
                summary.append("NENHUMA ATIVA");
            }

            return summary.toString();
        }

        String getShortActivePartsSummary() {
            return getActivePartsSummary();
        }
    }

    private static class Setlist {
        final String name;
        final List<Integer> bankIndices = new ArrayList<>();
        int currentMusicIndex = -1;

        Setlist(String name) {
            this.name = name;
        }

        void addBank(int index) {
            if (!bankIndices.contains(index)) {
                bankIndices.add(index);
            }
        }

        void removeBank(int index) {
            bankIndices.remove(Integer.valueOf(index));
        }

        void moveBankUp(int index) {
            int pos = bankIndices.indexOf(index);
            if (pos > 0) {
                Collections.swap(bankIndices, pos, pos - 1);
            }
        }

        void moveBankDown(int index) {
            int pos = bankIndices.indexOf(index);
            if (pos >= 0 && pos < bankIndices.size() - 1) {
                Collections.swap(bankIndices, pos, pos + 1);
            }
        }
    }
}
