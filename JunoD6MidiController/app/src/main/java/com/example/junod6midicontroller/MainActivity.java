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

public class MainActivity extends Activity {
    private static final String PREFS_NAME = "juno_d6_midi_prefs";
    private static final String KEY_BANKS_JSON = "banks_json";
    private static final String KEY_SELECTED_THEME = "selected_theme";

    private static final int THEME_AMBER = 0;
    private static final int THEME_MATRIX = 1;
    private static final int THEME_NEON = 2;

    private final List<Bank> banks = new ArrayList<>();

    private Spinner spinnerBanks;
    private LinearLayout layoutPresets;
    private TextView tvEmptyPresets;
    private Button btnNewPreset;
    private Button btnDeleteBank;
    private TextView tvMidiStatus;
    private TextView tvMidiStatusDot;

    private int selectedBankIndex = -1;
    private int activePresetIndex = -1;
    private int selectedTheme = THEME_AMBER;
    private boolean performanceMode = false;

    private MidiManager midiManager;
    private MidiDeviceInfo selectedMidiDeviceInfo;
    private MidiDevice midiDevice;
    private MidiInputPort midiInputPort;
    private MidiOutputPort midiOutputPort;
    private MidiReceiver midiReceiver;
    private ThemePalette palette;

    private final Handler mainHandler = new Handler(Looper.getMainLooper());

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        loadSelectedTheme();
        applySelectedTheme();
        configureSystemBars();
        loadBanks();

        midiManager = (MidiManager) getSystemService(Context.MIDI_SERVICE);
        
        buildMainScreen();
    }

    @Override
    protected void onResume() {
        super.onResume();
        refreshMidiDevices();
        openMidiDeviceIfNeeded();
    }

    @Override
    protected void onPause() {
        closeMidiDevice();
        super.onPause();
    }

    @Override
    public void onBackPressed() {
        if (performanceMode) {
            showEditorMode();
        } else {
            super.onBackPressed();
        }
    }

    private void loadSelectedTheme() {
        SharedPreferences preferences = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        selectedTheme = preferences.getInt(KEY_SELECTED_THEME, THEME_AMBER);
        if (selectedTheme < THEME_AMBER || selectedTheme > THEME_NEON) {
            selectedTheme = THEME_AMBER;
        }
    }

    private void saveSelectedTheme() {
        getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                .edit()
                .putInt(KEY_SELECTED_THEME, selectedTheme)
                .apply();
    }

    private void applySelectedTheme() {
        if (selectedTheme == THEME_MATRIX) {
            palette = new ThemePalette(R.drawable.background_matrix,
                    Color.rgb(5, 8, 7), Color.argb(135, 0, 0, 0),
                    Color.argb(176, 0, 5, 2), Color.argb(195, 0, 4, 2), Color.argb(187, 0, 3, 1),
                    Color.rgb(80, 255, 0), Color.rgb(150, 255, 60), Color.rgb(24, 120, 8),
                    Color.rgb(220, 255, 210), Color.rgb(125, 205, 95), Color.rgb(3, 20, 2),
                    Color.rgb(22, 112, 52), Color.rgb(10, 33, 17),
                    Color.rgb(80, 255, 0), Color.rgb(20, 105, 5), Color.rgb(130, 255, 45));
            return;
        }
        if (selectedTheme == THEME_NEON) {
            palette = new ThemePalette(
                    R.drawable.background_neon,

                    Color.rgb(3, 5, 24),
                    Color.argb(115, 0, 0, 0),
                    Color.argb(205, 3, 5, 24),
                    Color.argb(225, 2, 3, 18),
                    Color.argb(215, 2, 2, 14),

                    // Azul dominante
                    Color.rgb(35, 150, 255),
                    Color.rgb(120, 215, 255),
                    Color.rgb(12, 70, 155),

                    // Textos
                    Color.rgb(225, 245, 255),
                    Color.rgb(130, 185, 220),
                    Color.rgb(4, 12, 28),

                    // Bordas discretas
                    Color.rgb(24, 60, 110),
                    Color.rgb(9, 18, 45),

                    // Magenta somente como detalhe
                    Color.rgb(190, 45, 210),
                    Color.rgb(105, 20, 125),
                    Color.rgb(235, 130, 245)
            );
            return;
        }
        palette = new ThemePalette(R.drawable.background_amber,
                Color.rgb(10, 9, 5), Color.argb(135, 0, 0, 0),
                Color.argb(176, 5, 4, 1), Color.argb(195, 4, 3, 1), Color.argb(187, 3, 3, 1),
                Color.rgb(255, 145, 35), Color.rgb(255, 195, 105), Color.rgb(170, 78, 12),
                Color.rgb(255, 238, 210), Color.rgb(220, 158, 92), Color.rgb(28, 12, 3),
                Color.rgb(106, 77, 21), Color.rgb(18, 15, 6),
                Color.rgb(255, 145, 35), Color.rgb(150, 65, 10), Color.rgb(255, 185, 90));
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
        if (performanceMode) buildPerformanceScreen(); else buildMainScreen();
    }

    private void showEditorMode() {
        performanceMode = false;
        activePresetIndex = -1;
        buildMainScreen();
    }

    private void showPerformanceMode() {
        if (!hasSelectedBank()) {
            Toast.makeText(this, "Crie ou selecione uma música antes de entrar no modo Performance.", Toast.LENGTH_SHORT).show();
            return;
        }
        performanceMode = true;
        activePresetIndex = -1;
        buildPerformanceScreen();
    }

    private void sortBanksAlphabetically() {
        Collections.sort(
                banks,
                new Comparator<Bank>() {
                    @Override
                    public int compare(Bank first, Bank second) {
                        return first.name.compareToIgnoreCase(second.name);
                    }
                }
        );
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
        addPerformanceButton(content);
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

        /*
        * Cabeçalho fixo:
        * sem seletor de tema, sem caixa externa e sem texto auxiliar.
        */
        TextView title = createPrimaryText(
                "PERFORMANCE MODE",
                12,
                true
        );
        title.setLetterSpacing(0.10f);

        content.addView(
                title,
                marginParams(
                        -1,
                        -2,
                        dp(4),
                        0,
                        dp(4),
                        dp(8)
                )
        );

        Button song = createSongSelectorButton();
        song.setOnClickListener(v -> showPerformanceSongSelector(song));

        content.addView(
                song,
                marginParams(
                        -1,
                        dp(52),
                        0,
                        0,
                        0,
                        dp(10)
                )
        );

        LinearLayout midi = new LinearLayout(this);
        midi.setGravity(Gravity.CENTER_VERTICAL);
        midi.setPadding(dp(12), dp(8), dp(12), dp(8));
        midi.setBackground(
                createPanelBackground(
                        palette.panelBackground,
                        palette.secondaryAccentDark,
                        14
                )
        );

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
                marginParams(
                        -1,
                        -2,
                        0,
                        0,
                        0,
                        dp(10)
                )
        );

        /*
        * A área de pads ocupa somente o espaço entre o cabeçalho e o botão Voltar.
        * Não há ScrollView nesta tela.
        */
        LinearLayout pads = new LinearLayout(this);
        pads.setOrientation(LinearLayout.VERTICAL);

        addPerformancePads(pads);

        content.addView(
                pads,
                new LinearLayout.LayoutParams(
                        -1,
                        0,
                        1
                )
        );

        Button back = createOutlineButton("← VOLTAR PARA EDIÇÃO");
        back.setOnClickListener(v -> showEditorMode());

        content.addView(
                back,
                marginParams(
                        -1,
                        dp(50),
                        0,
                        dp(10),
                        0,
                        0
                )
        );

        root.addView(
                content,
                new FrameLayout.LayoutParams(-1, -1)
        );

        setContentView(root);
    }

    private void addThemeSelector(LinearLayout parent) {
        LinearLayout row = new LinearLayout(this);
        row.setPadding(0, 0, 0, dp(12));
        Button amber = createThemeButton("ÂMBAR", THEME_AMBER);
        Button matrix = createThemeButton("MATRIX", THEME_MATRIX);
        Button neon = createThemeButton("NEON", THEME_NEON);
        amber.setOnClickListener(v -> selectTheme(THEME_AMBER));
        matrix.setOnClickListener(v -> selectTheme(THEME_MATRIX));
        neon.setOnClickListener(v -> selectTheme(THEME_NEON));
        row.addView(amber, new LinearLayout.LayoutParams(0, dp(29), 1));
        row.addView(matrix, marginParams(0, dp(29), dp(7), 0, dp(7), 0, 1));
        row.addView(neon, new LinearLayout.LayoutParams(0, dp(29), 1));
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
            button.setBackground(
                    createRaisedBackground(
                            accent,
                            bright,
                            dark,
                            16
                    )
            );
            applyDarkTextShadow(button);
            applyButtonElevation(button, 4);
        } else {
            button.setTextColor(accent);
            button.setBackground(
                    createDarkRaisedBackground(
                            palette.panelBackground,
                            dark,
                            accent,
                            16
                    )
            );
            applyGlow(button, accent, 1.2f, 100);
            applyButtonElevation(button, 2);
        }

        return button;
    }

    private int getThemeAccent(int id) { return id == THEME_MATRIX ? Color.rgb(0,232,58) : id == THEME_NEON ? Color.rgb(222,40,157) : Color.rgb(255,176,0); }
    private int getThemeAccentBright(int id) { return id == THEME_MATRIX ? Color.rgb(131,255,155) : id == THEME_NEON ? Color.rgb(255,119,210) : Color.rgb(255,214,100); }
    private int getThemeAccentDark(int id) { return id == THEME_MATRIX ? Color.rgb(10,122,43) : id == THEME_NEON ? Color.rgb(118,25,101) : Color.rgb(173,112,0); }
    private int getThemeTextDark(int id) { return id == THEME_MATRIX ? Color.rgb(3,22,7) : id == THEME_NEON ? Color.rgb(21,6,23) : Color.rgb(20,15,4); }

    private void addEditorHeader(LinearLayout parent) {
        LinearLayout header = new LinearLayout(this);
        header.setOrientation(LinearLayout.VERTICAL);
        header.setPadding(dp(18), dp(18), dp(18), dp(18));
        header.setBackground(createPanelBackground(palette.panelBackground, palette.border, 18));
        header.addView(createPrimaryText("JUNO-D6", 28, true));
        header.addView(createAccentText("SCENE PART CONTROLLER", 11, true), marginParams(-1, -2, 0, dp(4), 0, dp(10)));
        header.addView(createSecondaryText("Bancos, presets e controle de Parts para performance ao vivo.", 13, false));
        parent.addView(header, marginParams(-1, -2, 0, 0, 0, dp(16)));
    }

    private void addMidiSection(LinearLayout parent) {
        parent.addView(createSectionTitle("CONEXÃO MIDI"));
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(dp(14), dp(14), dp(14), dp(14));
        card.setBackground(createPanelBackground(palette.panelBackground, palette.border, 14));
        LinearLayout statusRow = new LinearLayout(this);
        statusRow.setGravity(Gravity.CENTER_VERTICAL);
        tvMidiStatusDot = createSecondaryText("●", 18, false);
        statusRow.addView(tvMidiStatusDot);
        TextView statusLabel = createSecondaryText("STATUS USB-MIDI", 11, true);
        statusLabel.setPadding(dp(8), 0, 0, 0);
        statusRow.addView(statusLabel);
        card.addView(statusRow);
        tvMidiStatus = createPrimaryText("Verificando dispositivos MIDI...", 14, false);
        tvMidiStatus.setPadding(0, dp(8), 0, dp(12));
        card.addView(tvMidiStatus);
        Button refresh = createOutlineButton("ATUALIZAR DISPOSITIVOS MIDI");
        refresh.setOnClickListener(v -> refreshMidiDevices());
        card.addView(refresh, new LinearLayout.LayoutParams(-1, dp(34)));
        parent.addView(card, marginParams(-1, -2, 0, 0, 0, dp(10)));
    }

    private void addPerformanceButton(LinearLayout parent) {
        Button button = createPrimaryActionButton("▶ MODO PERFORMANCE");
        button.setOnClickListener(v -> showPerformanceMode());
        parent.addView(button, marginParams(-1, dp(42), 0, 0, 0, dp(18)));
    }

    private void addBankSection(LinearLayout parent) {
        parent.addView(createSectionTitle("MÚSICAS"));
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(dp(12), dp(10), dp(12), dp(12));
        card.setBackground(createPanelBackground(palette.panelBackground, palette.border, 14));
        spinnerBanks = new Spinner(this);
        spinnerBanks.setPadding(dp(8), 0, dp(8), 0);
        spinnerBanks.setBackground(createRoundedBackground(palette.panelStrong, palette.secondaryAccentDark, 12, 1));
        card.addView(spinnerBanks, new LinearLayout.LayoutParams(-1, dp(50)));
        LinearLayout buttons = new LinearLayout(this);
        Button add = createPrimaryActionButton("+ NOVA MÚSICA");
        add.setOnClickListener(v -> showCreateBankDialog());
        btnDeleteBank = createOutlineButton("EXCLUIR");
        btnDeleteBank.setOnClickListener(v -> showDeleteBankDialog());
        buttons.addView(add, new LinearLayout.LayoutParams(0, dp(34), 1));
        buttons.addView(btnDeleteBank, marginParams(0, dp(34), dp(8), 0, 0, 0, 1));
        card.addView(buttons, marginParams(-1, -2, 0, dp(10), 0, 0));
        parent.addView(card, marginParams(-1, -2, 0, 0, 0, dp(18)));
    }

    private void addPresetSection(LinearLayout parent) {
        LinearLayout header = new LinearLayout(this);
        header.addView(createSectionTitle("PRESETS"), new LinearLayout.LayoutParams(0, dp(28), 1));
        header.addView(createSecondaryText("toque no card para editar", 11, false));
        parent.addView(header);
        btnNewPreset = createPrimaryActionButton("+ NOVO PRESET");
        btnNewPreset.setOnClickListener(v -> { if (hasSelectedBank()) showPresetEditor(null, -1); });
        parent.addView(btnNewPreset, marginParams(-1, dp(37), 0, 0, 0, dp(10)));
        tvEmptyPresets = createSecondaryText("Nenhum preset nesta música.", 15, false);
        tvEmptyPresets.setGravity(Gravity.CENTER);
        tvEmptyPresets.setPadding(dp(16), dp(24), dp(16), dp(24));
        tvEmptyPresets.setBackground(createPanelBackground(palette.panelBackground, palette.border, 14));
        parent.addView(tvEmptyPresets, marginParams(-1, -2, 0, 0, 0, dp(8)));
        layoutPresets = new LinearLayout(this);
        layoutPresets.setOrientation(LinearLayout.VERTICAL);
        parent.addView(layoutPresets);
    }

    private String getSelectedBankName() {
        if (selectedBankIndex >= 0
                && selectedBankIndex < banks.size()) {
            return banks.get(selectedBankIndex).name;
        }

        return "SEM MÚSICA";
    }

    private Button createSongSelectorButton() {
        Button button = createOutlineButton(getSelectedBankName().toUpperCase() + "  ▾");
        button.setTextSize(22);
        return button;
    }

    private void showPerformanceSongSelector(View anchor) {
        if (banks.isEmpty()) return;
        LinearLayout content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(dp(6), dp(6), dp(6), dp(6));
        final PopupWindow popup = new PopupWindow(content, anchor.getWidth(), -2, true);
        popup.setBackgroundDrawable(createRoundedBackground(palette.panelStrong, palette.secondaryAccentDark, 16, 1));
        popup.setOutsideTouchable(true);
        for (int i = 0; i < banks.size(); i++) {
            final int index = i;
            Button button = createOutlineButton(banks.get(i).name.toUpperCase());
            button.setOnClickListener(v -> { selectedBankIndex = index; activePresetIndex = -1; selectSongScene(banks.get(index)); popup.dismiss(); buildPerformanceScreen(); });
            content.addView(button, marginParams(-1, dp(52), 0, 0, 0, dp(4)));
        }
        popup.showAsDropDown(anchor, 0, dp(4));
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
                        marginParams(
                                -1,
                                dp(92),
                                0,
                                0,
                                0,
                                dp(8)
                        )
                );
                continue;
            }

            LinearLayout row = new LinearLayout(this);
            row.setOrientation(LinearLayout.HORIZONTAL);

            row.addView(
                    createPerformancePad(bank.presets.get(i), i),
                    new LinearLayout.LayoutParams(
                            0,
                            dp(92),
                            1
                    )
            );

            row.addView(
                    createPerformancePad(bank.presets.get(i + 1), i + 1),
                    marginParams(
                            0,
                            dp(92),
                            dp(10),
                            0,
                            0,
                            0,
                            1
                    )
            );

            parent.addView(
                    row,
                    marginParams(
                            -1,
                            dp(92),
                            0,
                            0,
                            0,
                            dp(8)
                    )
            );
        }
    }

private LinearLayout createPerformancePad(Preset preset, int index) {
    boolean active = index == activePresetIndex;

    LinearLayout pad = new LinearLayout(this);
    pad.setOrientation(LinearLayout.VERTICAL);
    pad.setGravity(Gravity.CENTER);
    pad.setPadding(dp(8), dp(8), dp(8), dp(8));

    pad.setBackground(
            createRoundedBackground(
                    active ? palette.accent : palette.padBackground,
                    active
                            ? palette.accentBright
                            : palette.secondaryAccentDark,
                    20,
                    active ? 2 : 1
            )
    );

    TextView presetName = createPrimaryText(
            preset.name.toUpperCase(),
            16,
            true
    );

    if (active) {
        presetName.setTextColor(palette.textDark);
    }

    presetName.setGravity(Gravity.CENTER);
    presetName.setMaxLines(1);

    TextView parts = createSecondaryText(
            preset.getShortActivePartsSummary(),
            10,
            false
    );

    if (active) {
        parts.setTextColor(palette.textDark);
    }

    parts.setGravity(Gravity.CENTER);
    parts.setPadding(0, dp(4), 0, 0);

    pad.addView(presetName);
    pad.addView(parts);

    pad.setOnClickListener(v -> {
        activePresetIndex = index;
        applyPreset(banks.get(selectedBankIndex), preset);
        buildPerformanceScreen();
    });

    return pad;
}

    private void refreshMidiDevices() {
        if (midiManager == null) return;
        MidiDeviceInfo[] devices = midiManager.getDevices();
        if (devices == null || devices.length == 0) {
            selectedMidiDeviceInfo = null;
            if (tvMidiStatus != null) tvMidiStatus.setText("Nenhum dispositivo MIDI USB detectado.");
            return;
        }
        selectedMidiDeviceInfo = devices[0];
        if (tvMidiStatus != null) tvMidiStatus.setText(getMidiDeviceName(selectedMidiDeviceInfo) + "\nMIDI detectado.");
    }

    private void openMidiDeviceIfNeeded() {
        closeMidiDevice();

        if (midiManager == null || selectedMidiDeviceInfo == null) {
            return;
        }

        midiManager.openDevice(selectedMidiDeviceInfo, device -> {
            if (device == null) {
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
                        public void onSend(
                                byte[] message,
                                int offset,
                                int count,
                                long timestamp
                        ) {
                            StringBuilder hex = new StringBuilder();

                            for (int i = offset; i < offset + count; i++) {
                                hex.append(
                                        String.format(
                                                "%02X ",
                                                message[i] & 0xFF
                                        )
                                );
                            }
                            /* 

                            String receivedMessage = hex.toString().trim();

                            if (receivedMessage.startsWith("F0 41")) {
                                runOnUiThread(() -> showMidiReceivedDialog(receivedMessage));
                            } 
                            */                           
                        }
                    };

                    midiOutputPort.connect(midiReceiver);
                }
            }
        }, null);
    }

    private void showMidiReceivedDialog(String receivedMessage) {
        TextView messageView = new TextView(this);

        messageView.setText(receivedMessage);
        messageView.setTextSize(16);
        messageView.setTextColor(Color.WHITE);
        messageView.setPadding(dp(24), dp(18), dp(24), dp(18));
        messageView.setTextIsSelectable(true);

        ScrollView scrollView = new ScrollView(this);
        scrollView.addView(messageView);

        AlertDialog dialog = new AlertDialog.Builder(this)
                .setTitle("MIDI RECEBIDO")
                .setView(scrollView)
                .setPositiveButton("FECHAR", null)
                .create();

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
            Toast.makeText(
                    this,
                    "Scene inválida no app: " + sceneNumber,
                    Toast.LENGTH_LONG
            ).show();
            return;
        }

        if (selectedMidiDeviceInfo == null) {
            Toast.makeText(
                    this,
                    "MIDI: nenhum dispositivo selecionado.",
                    Toast.LENGTH_LONG
            ).show();
            return;
        }

        if (midiInputPort == null) {
            openMidiDeviceIfNeeded();

            Toast.makeText(
                    this,
                    "MIDI reconectando. Troque a música novamente em 1 segundo.",
                    Toast.LENGTH_LONG
            ).show();
            return;
        }

        int midiChannel = 15;
        int bankMsb = 85;
        int bankLsb = 0;
        int program = sceneNumber - 1;

        byte[] messages = new byte[]{
                (byte) (0xB0 | midiChannel),
                (byte) 0,
                (byte) bankMsb,
    
                (byte) (0xB0 | midiChannel),
                (byte) 32,
                (byte) bankLsb,
    
                (byte) (0xC0 | midiChannel),
                (byte) program
        };
    
        try {
            midiInputPort.send(messages, 0, messages.length);

            Toast.makeText(
                    this,
                    "Enviado USER " + sceneNumber
                            + " para "
                            + getMidiDeviceName(selectedMidiDeviceInfo),
                    Toast.LENGTH_LONG
            ).show();
        } catch (IOException exception) {
            closeMidiDevice();
            openMidiDeviceIfNeeded();

            Toast.makeText(
            this,
            "Conexão MIDI caiu e está sendo reaberta. "
                  + "Troque a música novamente em 1 segundo.",
            Toast.LENGTH_LONG
        ).show();
        }
    }

    private void testPart1On() {
        if (midiInputPort == null) {
            openMidiDeviceIfNeeded();
            if (midiInputPort == null) {
                Toast.makeText(
                        this,
                        "MIDI: porta ainda não foi aberta.",
                        Toast.LENGTH_LONG
                ).show();
                return;
            }
        }

        byte[] sysex = new byte[] {
                (byte) 0xF0,
                (byte) 0x11,
                (byte) 0x00,
                (byte) 0x00,
                (byte) 0x41,
                (byte) 0x10,
                (byte) 0x00,
                (byte) 0x00,
                (byte) 0x06,
                (byte) 0x01,
                (byte) 0xF7
        };

        try {
            midiInputPort.send(sysex, 0, sysex.length);
            Toast.makeText(
                    this,
                    "Part 1: ON enviado",
                    Toast.LENGTH_SHORT
            ).show();
        } catch (IOException exception) {
            closeMidiDevice();
            openMidiDeviceIfNeeded();

            Toast.makeText(
                    this,
                    "Erro ao enviar Part 1: " + exception.getMessage(),
                    Toast.LENGTH_LONG
            ).show();
        }
    }

    private void selectSongScene(Bank bank) {
        if (bank != null) sendUserScene(bank.scene);
    }

    private String getMidiDeviceName(MidiDeviceInfo info) {
        if (info == null) return "Dispositivo desconhecido";
        String name = info.getProperties().getString(MidiDeviceInfo.PROPERTY_NAME);
        return name == null || name.trim().isEmpty() ? "Dispositivo MIDI #" + info.getId() : name;
    }

    private void configureBankSpinner() {
        if (spinnerBanks == null) return;
        List<String> names = new ArrayList<>();
        for (Bank bank : banks) names.add(bank.name);
        if (names.isEmpty()) names.add("Nenhuma música criada");
        spinnerBanks.setAdapter(new ArrayAdapter<String>(this, android.R.layout.simple_spinner_item, names));
        spinnerBanks.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                selectedBankIndex = banks.isEmpty() ? -1 : position;
                if (hasSelectedBank()) selectSongScene(banks.get(selectedBankIndex));
                refreshPresetList();
            }
            @Override public void onNothingSelected(AdapterView<?> parent) { selectedBankIndex = -1; refreshPresetList(); }
        });
        if (banks.isEmpty()) { selectedBankIndex = -1; spinnerBanks.setEnabled(false); }
        else { spinnerBanks.setEnabled(true); if (!hasSelectedBank()) selectedBankIndex = 0; spinnerBanks.setSelection(selectedBankIndex); }
        refreshPresetList();
    }

    private void refreshPresetList() {
        if (layoutPresets == null) return;
        layoutPresets.removeAllViews();
        boolean selected = hasSelectedBank();
        btnNewPreset.setEnabled(selected);
        btnDeleteBank.setEnabled(selected);
        if (!selected) { tvEmptyPresets.setText("Crie uma música para começar."); tvEmptyPresets.setVisibility(View.VISIBLE); return; }
        Bank bank = banks.get(selectedBankIndex);
        if (bank.presets.isEmpty()) { tvEmptyPresets.setVisibility(View.VISIBLE); return; }
        tvEmptyPresets.setVisibility(View.GONE);
        for (int i = 0; i < bank.presets.size(); i++) addPresetCard(bank, bank.presets.get(i), i);
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
        card.addView(createSecondaryText(preset.getActivePartsSummary(), 13, false), marginParams(-1, -2, 0, dp(8), 0, dp(12)));
        LinearLayout actionRow = new LinearLayout(this);
        actionRow.setOrientation(LinearLayout.HORIZONTAL);

        Button apply = createPrimaryActionButton("APLICAR");
        apply.setOnClickListener(v -> applyPreset(bank, preset));

        Button delete = createOutlineButton("EXCLUIR");
        delete.setOnClickListener(v -> showDeletePresetDialog(preset, index));

        actionRow.addView(
                apply,
                new LinearLayout.LayoutParams(0, dp(32), 1)
        );

        actionRow.addView(
                delete,
                marginParams(
                        0,
                        dp(44),
                        dp(8),
                        0,
                        0,
                        0,
                        1
                )
        );

        card.addView(actionRow);
        layoutPresets.addView(card, marginParams(-1, -2, 0, 0, 0, dp(10)));
    }

    private void showCreateBankDialog() {
        LinearLayout form = new LinearLayout(this);
        form.setOrientation(LinearLayout.VERTICAL);
        form.setPadding(dp(18), dp(8), dp(18), dp(4));
        form.addView(createDialogLabel("NOME DA MÚSICA"));
        EditText inputName = createThemeEditText("Ex.: Enjoy the Silence");
        form.addView(inputName);
        TextView label = createDialogLabel("USER SCENE (1 A 128)");
        label.setPadding(0, dp(16), 0, dp(6));
        form.addView(label);
        EditText inputScene = createThemeEditText("Ex.: 12");
        inputScene.setInputType(2);
        inputScene.setText("1");
        form.addView(inputScene);
        AlertDialog dialog = new AlertDialog.Builder(this).setTitle("NOVA MÚSICA").setView(form).setNegativeButton("CANCELAR", null).setPositiveButton("CRIAR", null).create();
        dialog.setOnShowListener(v -> dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(x -> {
            String name = inputName.getText().toString().trim();
            int scene = parseSceneNumber(inputScene.getText().toString());
            if (name.isEmpty() || scene < 1 || scene > 128) return;
            Bank bank = new Bank(name, scene);
            banks.add(bank);
            sortBanksAlphabetically();

            selectedBankIndex = banks.indexOf(bank);

            saveBanks();
            configureBankSpinner();
            spinnerBanks.setSelection(selectedBankIndex);
            selectSongScene(bank);
            dialog.dismiss();
        }));
        dialog.show();
    }

    private void showDeleteBankDialog() {
        if (!hasSelectedBank()) return;
        new AlertDialog.Builder(this).setTitle("EXCLUIR MÚSICA?").setMessage("A música e seus presets serão apagados.").setNegativeButton("CANCELAR", null).setPositiveButton("EXCLUIR", (d, w) -> { banks.remove(selectedBankIndex); selectedBankIndex = -1; saveBanks(); configureBankSpinner(); }).show();
    }

    private void showDeletePresetDialog(Preset preset, int presetIndex) {
        if (!hasSelectedBank()) {
            return;
        }

        AlertDialog dialog = new AlertDialog.Builder(this)
                .setTitle("EXCLUIR PRESET?")
                .setMessage(
                        "O preset \"" + preset.name
                                + "\" será apagado desta música."
                )
                .setNegativeButton("CANCELAR", null)
                .setPositiveButton("EXCLUIR", null)
                .create();

        dialog.setOnShowListener(listener -> {
            Button deleteButton = dialog.getButton(
                    AlertDialog.BUTTON_POSITIVE
            );

            if (deleteButton != null) {
                deleteButton.setTextColor(palette.accent);

                deleteButton.setOnClickListener(view -> {
                    Bank bank = banks.get(selectedBankIndex);

                    if (presetIndex >= 0
                            && presetIndex < bank.presets.size()) {
                        bank.presets.remove(presetIndex);
                        saveBanks();
                        refreshPresetList();

                        Toast.makeText(
                                this,
                                "Preset excluído.",
                                Toast.LENGTH_SHORT
                        ).show();
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
        if (editing) inputName.setText(preset.name);
        form.addView(createDialogLabel("NOME DO PRESET"));
        form.addView(inputName);
        form.addView(createDialogLabel("PARTS ATIVAS"), marginParams(-1, -2, 0, dp(16), 0, dp(4)));
        boolean[] states = new boolean[8];
        if (editing) System.arraycopy(preset.partStates, 0, states, 0, 8); else states[0] = true;
        LinearLayout grid = new LinearLayout(this);
        grid.setOrientation(LinearLayout.VERTICAL);
        for (int row = 0; row < 4; row++) {
            LinearLayout line = new LinearLayout(this);
            for (int col = 0; col < 2; col++) {
                int part = row * 2 + col;
                Button button = createPartButton(part, states[part]);
                final int p = part;
                button.setOnClickListener(v -> { states[p] = !states[p]; updatePartButton(button, p, states[p]); });
                line.addView(button, marginParams(0, dp(56), col == 1 ? dp(8) : 0, dp(4), 0, dp(4), 1));
            }
            grid.addView(line);
        }
        form.addView(grid);
        AlertDialog dialog = new AlertDialog.Builder(this).setTitle(editing ? "EDITAR PRESET" : "NOVO PRESET").setView(form).setNegativeButton("CANCELAR", null).setPositiveButton("SALVAR", null).create();
        dialog.setOnShowListener(v -> dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(x -> {
            String name = inputName.getText().toString().trim();
            if (name.isEmpty() || !hasSelectedBank()) return;
            if (editing) { preset.name = name; System.arraycopy(states, 0, preset.partStates, 0, 8); }
            else banks.get(selectedBankIndex).presets.add(new Preset(name, 1, states));
            saveBanks(); refreshPresetList(); dialog.dismiss();
        }));
        dialog.show();
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

                /*
                * Mapa real capturado do JUNO:
                *
                * Part 1 = 01 00 21 00
                * Part 2 = 01 00 22 00
                * ...
                * Part 8 = 01 00 28 00
                */
                int addressPart = 0x20 + partNumber;

                /*
                * Estado salvo no preset:
                * true  = ON  = 01
                * false = OFF = 00
                */
                int value = preset.partStates[partIndex] ? 0x01 : 0x00;

                /*
                * Checksum Roland:
                * 128 - (soma dos bytes de endereço e dados)
                */
                int checksum = (
                        128 - (
                                (
                                        0x01
                                                + 0x00
                                                + addressPart
                                                + 0x00
                                                + value
                                ) % 128
                        )
                ) & 0x7F;

                byte[] message = new byte[] {
                        (byte) 0xF0,
                        (byte) 0x41,
                        (byte) 0x10,
                        (byte) 0x01,
                        (byte) 0x05,
                        (byte) 0x0A,
                        (byte) 0x12,

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
                object.put("name", bank.name);
                object.put("scene", bank.scene);
                JSONArray presets = new JSONArray();
                for (Preset preset : bank.presets) {
                    JSONObject p = new JSONObject();
                    p.put("name", preset.name);
                    p.put("scene", preset.scene);
                    JSONArray states = new JSONArray();
                    for (boolean state : preset.partStates) states.put(state);
                    p.put("partStates", states);
                    presets.put(p);
                }
                object.put("presets", presets);
                array.put(object);
            }
        } catch (JSONException ignored) { return; }
        getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit().putString(KEY_BANKS_JSON, array.toString()).apply();
    }

    private void loadBanks() {
        banks.clear();
        String saved = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).getString(KEY_BANKS_JSON, null);
        if (saved == null || saved.trim().isEmpty()) return;
        try {
            JSONArray array = new JSONArray(saved);
            for (int i = 0; i < array.length(); i++) {
                JSONObject object = array.getJSONObject(i);
                int scene = object.optInt("scene", 1);
                Bank bank = new Bank(object.optString("name", "Música sem nome"), scene);
                JSONArray presets = object.optJSONArray("presets");
                if (presets != null) for (int j = 0; j < presets.length(); j++) {
                    JSONObject p = presets.getJSONObject(j);
                    boolean[] states = new boolean[8];
                    JSONArray values = p.optJSONArray("partStates");
                    if (values != null) for (int k = 0; k < 8 && k < values.length(); k++) states[k] = values.optBoolean(k, false);
                    bank.presets.add(new Preset(p.optString("name", "Preset sem nome"), p.optInt("scene", 1), states));
                }
                banks.add(bank);
            }

            sortBanksAlphabetically();
        } catch (JSONException ignored) { 
            banks.clear(); 
        }
    }

    private TextView createSectionTitle(String text) { return createSecondaryText(text, 12, true); }
    private TextView createDialogLabel(String text) { return createSecondaryText(text, 11, true); }
    private TextView createPrimaryText(String text, float size, boolean bold) { TextView v = new TextView(this); v.setText(text); v.setTextSize(size); v.setTextColor(palette.textPrimary); if (bold) v.setTypeface(Typeface.DEFAULT_BOLD); return v; }
    private TextView createSecondaryText(String text, float size, boolean bold) { TextView v = new TextView(this); v.setText(text); v.setTextSize(size); v.setTextColor(palette.textSecondary); if (bold) v.setTypeface(Typeface.DEFAULT_BOLD); return v; }
    private TextView createAccentText(String text, float size, boolean bold) { TextView v = new TextView(this); v.setText(text); v.setTextSize(size); v.setTextColor(palette.accent); if (bold) v.setTypeface(Typeface.DEFAULT_BOLD); return v; }
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

        button.setBackground(
                createRaisedBackground(
                        palette.accent,
                        palette.accentBright,
                        palette.accentDark,
                        18
                )
        );

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

        button.setBackground(
                createDarkRaisedBackground(
                        palette.panelStrong,
                        palette.secondaryAccentDark,
                        palette.secondaryAccent,
                        18
                )
        );

        applyGlow(button, palette.secondaryAccent, 1.6f, 120);
        applyButtonElevation(button, 3);

        return button;
    }
    private EditText createThemeEditText(String hint) { EditText e = new EditText(this); e.setHint(hint); e.setTextColor(palette.textPrimary); e.setHintTextColor(palette.textSecondary); e.setSingleLine(true); e.setBackground(createRoundedBackground(palette.panelStrong, palette.secondaryAccentDark, 12, 1)); return e; }
    private Button createPartButton(int index, boolean enabled) { Button b = new Button(this); b.setAllCaps(false); updatePartButton(b, index, enabled); return b; }
    private void updatePartButton(
            Button button,
            int index,
            boolean enabled
    ) {
        button.setText(
                "PART " + (index + 1)
                        + "\n"
                        + (enabled ? "ON" : "OFF")
        );

        button.setTextSize(12);
        button.setTypeface(Typeface.DEFAULT_BOLD);
        button.setGravity(Gravity.CENTER);
        button.setPadding(dp(4), 0, dp(4), 0);

        if (enabled) {
            button.setTextColor(palette.textDark);
            button.setBackground(
                    createRaisedBackground(
                            palette.accent,
                            palette.accentBright,
                            palette.accentDark,
                            16
                    )
            );
            applyDarkTextShadow(button);
            applyButtonElevation(button, 3);
        } else {
            button.setTextColor(palette.secondaryAccent);
            button.setBackground(
                    createDarkRaisedBackground(
                            palette.partOff,
                            palette.secondaryAccentDark,
                            palette.secondaryAccent,
                            16
                    )
            );
            applyGlow(button, palette.secondaryAccent, 1.2f, 110);
            applyButtonElevation(button, 2);
        }
    }
    private int parseSceneNumber(String value) { try { return Integer.parseInt(value.trim()); } catch (NumberFormatException e) { return 0; } }
    private int dp(int value) { return Math.round(value * getResources().getDisplayMetrics().density); }
    private LinearLayout.LayoutParams marginParams(int width, int height, int left, int top, int right, int bottom) { LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(width, height); p.setMargins(left, top, right, bottom); return p; }
    private LinearLayout.LayoutParams marginParams(int width, int height, int left, int top, int right, int bottom, float weight) { LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(width, height, weight); p.setMargins(left, top, right, bottom); return p; }
    
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
                new int[] {
                        upperColor,
                        lowerColor
                }
        );

        drawable.setCornerRadius(dp(radiusDp));
        drawable.setStroke(dp(1), borderColor);

        return drawable;
    }

    private void applyButtonElevation(View view, int elevationDp) {
        view.setElevation(dp(elevationDp));
    }

    private void applyGlow(
            TextView view,
            int color,
            float radiusDp,
            int alpha
    ) {
        view.setLayerType(View.LAYER_TYPE_SOFTWARE, null);

        view.setShadowLayer(
                dpFloat(radiusDp),
                0f,
                dpFloat(1f),
                Color.argb(
                        alpha,
                        Color.red(color),
                        Color.green(color),
                        Color.blue(color)
                )
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

    private float dpFloat(float value) {
        return value * getResources().getDisplayMetrics().density;
    }

    private GradientDrawable createPanelBackground(
            int fill,
            int stroke,
            int radius
    ) {
        return createRoundedBackground(
                fill,
                stroke,
                radius,
                1
        );
    }
    private GradientDrawable createRoundedBackground(int fill, int stroke, int radius, int width) { GradientDrawable d = new GradientDrawable(); d.setColor(fill); d.setCornerRadius(dp(radius)); if (width > 0) d.setStroke(dp(width), stroke); return d; }

    private static class ThemePalette {
        final int backgroundImageResId, background, screenOverlay, panelBackground, panelStrong, padBackground, accent, accentBright, accentDark, textPrimary, textSecondary, textDark, border, partOff, secondaryAccent, secondaryAccentDark, activeSecondaryText;
        ThemePalette(int image, int bg, int overlay, int panel, int strong, int pad, int accent, int bright, int dark, int primary, int secondary, int textDark, int border, int partOff, int secondaryAccent, int secondaryDark, int active) { backgroundImageResId=image; background=bg; screenOverlay=overlay; panelBackground=panel; panelStrong=strong; padBackground=pad; this.accent=accent; accentBright=bright; accentDark=dark; textPrimary=primary; textSecondary=secondary; this.textDark=textDark; this.border=border; this.partOff=partOff; this.secondaryAccent=secondaryAccent; secondaryAccentDark=secondaryDark; activeSecondaryText=active; }
    }
    private static class Bank { final String name; int scene; final List<Preset> presets = new ArrayList<>(); Bank(String name) { this(name, 1); } Bank(String name, int scene) { this.name=name; this.scene=scene; } }
    private static class Preset { String name; int scene; final boolean[] partStates; Preset(String name, int scene, boolean[] states) { this.name=name; this.scene=scene; partStates=new boolean[8]; System.arraycopy(states, 0, partStates, 0, Math.min(8, states.length)); } String getActivePartsSummary() { StringBuilder s=new StringBuilder("PARTS: "); boolean any=false; for(int i=0;i<8;i++) if(partStates[i]) { if(any)s.append(", "); s.append(i+1); any=true; } if(!any)s.append("NENHUMA ATIVA"); return s.toString(); } String getShortActivePartsSummary() { return getActivePartsSummary(); } }
}
