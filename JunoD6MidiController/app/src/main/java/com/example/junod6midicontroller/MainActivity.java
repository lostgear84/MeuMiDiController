package com.example.junod6midicontroller;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.graphics.Typeface;
import android.os.Bundle;
import android.text.InputType;
import android.view.Gravity;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

public class MainActivity extends Activity {

    private static final String PREFS_NAME = "juno_d6_midi_prefs";
    private static final String KEY_BANKS_JSON = "banks_json";

    private final List<Bank> banks = new ArrayList<>();

    private Spinner spinnerBanks;
    private LinearLayout layoutPresets;
    private TextView tvEmptyPresets;
    private Button btnNewPreset;
    private Button btnDeleteBank;

    private int selectedBankIndex = -1;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        loadBanks();

        buildMainScreen();
    }

    private void buildMainScreen() {
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(16), dp(16), dp(16), dp(16));
        root.setBackgroundColor(Color.rgb(245, 245, 245));

        TextView title = createText(
                "Juno D6 MIDI Controller",
                22,
                true
        );
        title.setTextColor(Color.rgb(25, 25, 25));
        title.setPadding(0, 0, 0, dp(20));
        root.addView(title);

        TextView bankLabel = createText(
                "Banco / Música",
                16,
                true
        );
        bankLabel.setPadding(0, 0, 0, dp(8));
        root.addView(bankLabel);

        spinnerBanks = new Spinner(this);
        root.addView(
                spinnerBanks,
                new LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT,
                        LinearLayout.LayoutParams.WRAP_CONTENT
                )
        );

        LinearLayout bankButtonsRow = new LinearLayout(this);
        bankButtonsRow.setOrientation(LinearLayout.HORIZONTAL);
        bankButtonsRow.setPadding(0, dp(8), 0, 0);

        Button btnNewBank = createButton("Novo banco");
        btnNewBank.setOnClickListener(view -> showCreateBankDialog());

        btnDeleteBank = createButton("Excluir banco");
        btnDeleteBank.setOnClickListener(view -> showDeleteBankDialog());

        bankButtonsRow.addView(
                btnNewBank,
                new LinearLayout.LayoutParams(
                        0,
                        dp(48),
                        1
                )
        );

        LinearLayout.LayoutParams deleteBankParams = new LinearLayout.LayoutParams(
                0,
                dp(48),
                1
        );
        deleteBankParams.setMargins(dp(8), 0, 0, 0);

        bankButtonsRow.addView(btnDeleteBank, deleteBankParams);
        root.addView(bankButtonsRow);

        TextView presetLabel = createText(
                "Presets",
                16,
                true
        );
        presetLabel.setPadding(0, dp(24), 0, dp(8));
        root.addView(presetLabel);

        btnNewPreset = createButton("Novo preset");
        btnNewPreset.setOnClickListener(view -> {
            if (selectedBankIndex < 0 || selectedBankIndex >= banks.size()) {
                Toast.makeText(
                        this,
                        "Crie ou selecione um banco primeiro.",
                        Toast.LENGTH_SHORT
                ).show();
                return;
            }

            showPresetEditor(null, -1);
        });
        root.addView(
                btnNewPreset,
                new LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT,
                        dp(52)
                )
        );

        tvEmptyPresets = createText(
                "Nenhum preset neste banco.",
                15,
                false
        );
        tvEmptyPresets.setTextColor(Color.DKGRAY);
        tvEmptyPresets.setGravity(Gravity.CENTER);
        tvEmptyPresets.setPadding(0, dp(20), 0, dp(12));
        root.addView(tvEmptyPresets);

        ScrollView scrollView = new ScrollView(this);
        layoutPresets = new LinearLayout(this);
        layoutPresets.setOrientation(LinearLayout.VERTICAL);
        layoutPresets.setPadding(0, dp(8), 0, dp(8));

        scrollView.addView(
                layoutPresets,
                new ScrollView.LayoutParams(
                        ScrollView.LayoutParams.MATCH_PARENT,
                        ScrollView.LayoutParams.WRAP_CONTENT
                )
        );

        root.addView(
                scrollView,
                new LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT,
                        0,
                        1
                )
        );

        setContentView(root);

        configureBankSpinner();
    }

    private void configureBankSpinner() {
        List<String> bankNames = new ArrayList<>();

        for (Bank bank : banks) {
            bankNames.add(bank.name);
        }

        if (bankNames.isEmpty()) {
            bankNames.add("Nenhum banco criado");
        }

        ArrayAdapter<String> adapter = new ArrayAdapter<>(
                this,
                android.R.layout.simple_spinner_item,
                bankNames
        );

        adapter.setDropDownViewResource(
                android.R.layout.simple_spinner_dropdown_item
        );

        spinnerBanks.setAdapter(adapter);

        spinnerBanks.setOnItemSelectedListener(
                new AdapterView.OnItemSelectedListener() {
                    @Override
                    public void onItemSelected(
                            AdapterView<?> parent,
                            View view,
                            int position,
                            long id
                    ) {
                        if (banks.isEmpty()) {
                            selectedBankIndex = -1;
                        } else {
                            selectedBankIndex = position;
                        }

                        refreshPresetList();
                    }

                    @Override
                    public void onNothingSelected(AdapterView<?> parent) {
                        selectedBankIndex = -1;
                        refreshPresetList();
                    }
                }
        );

        if (banks.isEmpty()) {
            selectedBankIndex = -1;
            spinnerBanks.setEnabled(false);
        } else {
            spinnerBanks.setEnabled(true);
            selectedBankIndex = 0;
            spinnerBanks.setSelection(0);
        }

        refreshPresetList();
    }

    private void refreshPresetList() {
        layoutPresets.removeAllViews();

        boolean hasSelectedBank =
                selectedBankIndex >= 0 && selectedBankIndex < banks.size();

        btnNewPreset.setEnabled(hasSelectedBank);
        btnDeleteBank.setEnabled(hasSelectedBank);

        if (!hasSelectedBank) {
            tvEmptyPresets.setText(
                    "Crie um banco para começar.\nExemplo: nome da música ou show."
            );
            tvEmptyPresets.setVisibility(View.VISIBLE);
            return;
        }

        Bank bank = banks.get(selectedBankIndex);

        if (bank.presets.isEmpty()) {
            tvEmptyPresets.setText(
                    "Nenhum preset neste banco.\nToque em \"Novo preset\"."
            );
            tvEmptyPresets.setVisibility(View.VISIBLE);
            return;
        }

        tvEmptyPresets.setVisibility(View.GONE);

        for (int i = 0; i < bank.presets.size(); i++) {
            Preset preset = bank.presets.get(i);
            addPresetRow(bank, preset, i);
        }
    }

    private void addPresetRow(
            Bank bank,
            Preset preset,
            int presetIndex
    ) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(dp(4), dp(4), dp(4), dp(4));
        row.setBackgroundColor(Color.WHITE);

        LinearLayout textArea = new LinearLayout(this);
        textArea.setOrientation(LinearLayout.VERTICAL);
        textArea.setPadding(dp(12), dp(8), dp(4), dp(8));

        TextView presetName = createText(preset.name, 18, true);

        TextView details = createText(
                "Cena " + preset.scene + "  •  " + preset.getActivePartsSummary(),
                13,
                false
        );
        details.setTextColor(Color.DKGRAY);
        details.setPadding(0, dp(2), 0, 0);

        textArea.addView(presetName);
        textArea.addView(details);

        textArea.setOnClickListener(
                view -> showPresetEditor(preset, presetIndex)
        );

        Button btnPlay = createButton("Aplicar");
        btnPlay.setTextSize(13);
        btnPlay.setOnClickListener(view -> applyPreset(bank, preset));

        Button btnDelete = createButton("X");
        btnDelete.setTextSize(16);
        btnDelete.setOnClickListener(
                view -> showDeletePresetDialog(preset, presetIndex)
        );

        row.addView(
                textArea,
                new LinearLayout.LayoutParams(
                        0,
                        LinearLayout.LayoutParams.WRAP_CONTENT,
                        1
                )
        );

        row.addView(
                btnPlay,
                new LinearLayout.LayoutParams(
                        dp(84),
                        dp(48)
                )
        );

        LinearLayout.LayoutParams deletePresetParams =
                new LinearLayout.LayoutParams(
                        dp(48),
                        dp(48)
                );
        deletePresetParams.setMargins(dp(6), 0, 0, 0);

        row.addView(btnDelete, deletePresetParams);

        LinearLayout.LayoutParams rowParams =
                new LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT,
                        LinearLayout.LayoutParams.WRAP_CONTENT
                );
        rowParams.setMargins(0, dp(6), 0, dp(6));

        layoutPresets.addView(row, rowParams);
    }

    private void showCreateBankDialog() {
        EditText inputName = new EditText(this);
        inputName.setHint("Ex.: Enjoy the Silence");
        inputName.setSingleLine(true);
        inputName.setSelectAllOnFocus(true);
        inputName.setPadding(dp(20), dp(8), dp(20), dp(8));

        new AlertDialog.Builder(this)
                .setTitle("Novo banco / música")
                .setMessage("Digite o nome da música, setlist ou cena principal.")
                .setView(inputName)
                .setNegativeButton("Cancelar", null)
                .setPositiveButton("Criar", (dialog, which) -> {
                    String name = inputName.getText().toString().trim();

                    if (name.isEmpty()) {
                        Toast.makeText(
                                this,
                                "Digite um nome para o banco.",
                                Toast.LENGTH_SHORT
                        ).show();
                        return;
                    }

                    Bank newBank = new Bank(name);
                    banks.add(newBank);
                    saveBanks();

                    configureBankSpinner();

                    selectedBankIndex = banks.size() - 1;
                    spinnerBanks.setSelection(selectedBankIndex);

                    Toast.makeText(
                            this,
                            "Banco criado: " + name,
                            Toast.LENGTH_SHORT
                    ).show();
                })
                .show();
    }

    private void showDeleteBankDialog() {
        if (selectedBankIndex < 0 || selectedBankIndex >= banks.size()) {
            return;
        }

        Bank bank = banks.get(selectedBankIndex);

        new AlertDialog.Builder(this)
                .setTitle("Excluir banco?")
                .setMessage(
                        "O banco \"" + bank.name + "\" e todos os seus presets serão apagados."
                )
                .setNegativeButton("Cancelar", null)
                .setPositiveButton("Excluir", (dialog, which) -> {
                    banks.remove(selectedBankIndex);
                    saveBanks();
                    selectedBankIndex = -1;
                    configureBankSpinner();

                    Toast.makeText(
                            this,
                            "Banco excluído.",
                            Toast.LENGTH_SHORT
                    ).show();
                })
                .show();
    }

    private void showDeletePresetDialog(Preset preset, int presetIndex) {
        if (selectedBankIndex < 0 || selectedBankIndex >= banks.size()) {
            return;
        }

        new AlertDialog.Builder(this)
                .setTitle("Excluir preset?")
                .setMessage(
                        "O preset \"" + preset.name + "\" será apagado."
                )
                .setNegativeButton("Cancelar", null)
                .setPositiveButton("Excluir", (dialog, which) -> {
                    Bank bank = banks.get(selectedBankIndex);

                    if (presetIndex >= 0 && presetIndex < bank.presets.size()) {
                        bank.presets.remove(presetIndex);
                        saveBanks();
                        refreshPresetList();

                        Toast.makeText(
                                this,
                                "Preset excluído.",
                                Toast.LENGTH_SHORT
                        ).show();
                    }
                })
                .show();
    }

    private void showPresetEditor(
            Preset presetToEdit,
            int presetIndex
    ) {
        boolean isEditing = presetToEdit != null;

        ScrollView scrollView = new ScrollView(this);
        LinearLayout editor = new LinearLayout(this);
        editor.setOrientation(LinearLayout.VERTICAL);
        editor.setPadding(dp(20), dp(12), dp(20), dp(12));
        scrollView.addView(editor);

        TextView nameLabel = createText("Nome do preset", 15, true);
        editor.addView(nameLabel);

        EditText inputName = new EditText(this);
        inputName.setHint("Ex.: Verso, Refrão, Solo");
        inputName.setSingleLine(true);
        inputName.setInputType(InputType.TYPE_CLASS_TEXT);
        inputName.setText(isEditing ? presetToEdit.name : "");
        editor.addView(
                inputName,
                new LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT,
                        LinearLayout.LayoutParams.WRAP_CONTENT
                )
        );

        TextView sceneLabel = createText("Número da Scene", 15, true);
        sceneLabel.setPadding(0, dp(14), 0, 0);
        editor.addView(sceneLabel);

        EditText inputScene = new EditText(this);
        inputScene.setHint("Ex.: 1");
        inputScene.setSingleLine(true);
        inputScene.setInputType(InputType.TYPE_CLASS_NUMBER);
        inputScene.setText(
                isEditing ? String.valueOf(presetToEdit.scene) : "1"
        );
        editor.addView(
                inputScene,
                new LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT,
                        LinearLayout.LayoutParams.WRAP_CONTENT
                )
        );

        TextView partsLabel = createText(
                "Parts ativas neste preset",
                15,
                true
        );
        partsLabel.setPadding(0, dp(20), 0, dp(8));
        editor.addView(partsLabel);

        TextView helpLabel = createText(
                "Toque em cada botão para alternar entre ON e OFF.",
                13,
                false
        );
        helpLabel.setTextColor(Color.DKGRAY);
        helpLabel.setPadding(0, 0, 0, dp(8));
        editor.addView(helpLabel);

        boolean[] partStates = new boolean[8];

        if (isEditing) {
            for (int i = 0; i < 8; i++) {
                partStates[i] = presetToEdit.partStates[i];
            }
        } else {
            partStates[0] = true;
        }

        LinearLayout partsGrid = new LinearLayout(this);
        partsGrid.setOrientation(LinearLayout.VERTICAL);

        Button[] partButtons = new Button[8];

        for (int row = 0; row < 4; row++) {
            LinearLayout buttonRow = new LinearLayout(this);
            buttonRow.setOrientation(LinearLayout.HORIZONTAL);

            for (int col = 0; col < 2; col++) {
                int partIndex = (row * 2) + col;

                Button partButton = createButton("");
                partButton.setTextSize(15);

                updatePartButton(partButton, partIndex, partStates[partIndex]);

                final int currentPartIndex = partIndex;

                partButton.setOnClickListener(view -> {
                    partStates[currentPartIndex] = !partStates[currentPartIndex];
                    updatePartButton(
                            partButton,
                            currentPartIndex,
                            partStates[currentPartIndex]
                    );
                });

                LinearLayout.LayoutParams partParams =
                        new LinearLayout.LayoutParams(
                                0,
                                dp(52),
                                1
                        );

                if (col == 1) {
                    partParams.setMargins(dp(8), 0, 0, 0);
                }

                buttonRow.addView(partButton, partParams);
                partButtons[partIndex] = partButton;
            }

            LinearLayout.LayoutParams buttonRowParams =
                    new LinearLayout.LayoutParams(
                            LinearLayout.LayoutParams.MATCH_PARENT,
                            dp(52)
                    );
            buttonRowParams.setMargins(0, dp(4), 0, dp(4));

            partsGrid.addView(buttonRow, buttonRowParams);
        }

        editor.addView(partsGrid);

        AlertDialog dialog = new AlertDialog.Builder(this)
                .setTitle(isEditing ? "Editar preset" : "Novo preset")
                .setView(scrollView)
                .setNegativeButton("Cancelar", null)
                .setPositiveButton("Salvar", null)
                .create();

        dialog.setOnShowListener(listener -> {
            Button positiveButton = dialog.getButton(AlertDialog.BUTTON_POSITIVE);

            positiveButton.setOnClickListener(view -> {
                String name = inputName.getText().toString().trim();

                if (name.isEmpty()) {
                    inputName.setError("Digite um nome.");
                    inputName.requestFocus();
                    return;
                }

                int scene = parseSceneNumber(inputScene.getText().toString());

                if (scene < 1) {
                    inputScene.setError("Digite uma Scene válida (1 ou maior).");
                    inputScene.requestFocus();
                    return;
                }

                if (selectedBankIndex < 0 || selectedBankIndex >= banks.size()) {
                    Toast.makeText(
                            this,
                            "Nenhum banco selecionado.",
                            Toast.LENGTH_SHORT
                    ).show();
                    dialog.dismiss();
                    return;
                }

                Bank selectedBank = banks.get(selectedBankIndex);

                if (isEditing) {
                    presetToEdit.name = name;
                    presetToEdit.scene = scene;

                    for (int i = 0; i < 8; i++) {
                        presetToEdit.partStates[i] = partStates[i];
                    }

                    Toast.makeText(
                            this,
                            "Preset atualizado.",
                            Toast.LENGTH_SHORT
                    ).show();
                } else {
                    boolean[] statesToSave = new boolean[8];

                    for (int i = 0; i < 8; i++) {
                        statesToSave[i] = partStates[i];
                    }

                    selectedBank.presets.add(
                            new Preset(name, scene, statesToSave)
                    );

                    Toast.makeText(
                            this,
                            "Preset criado.",
                            Toast.LENGTH_SHORT
                    ).show();
                }

                saveBanks();
                refreshPresetList();
                dialog.dismiss();
            });
        });

        dialog.show();
    }

    private void updatePartButton(
            Button button,
            int partIndex,
            boolean enabled
    ) {
        String stateText = enabled ? "ON" : "OFF";

        button.setText(
                "Part " + (partIndex + 1) + "\n" + stateText
        );

        if (enabled) {
            button.setBackgroundColor(Color.rgb(46, 125, 50));
            button.setTextColor(Color.WHITE);
        } else {
            button.setBackgroundColor(Color.rgb(110, 110, 110));
            button.setTextColor(Color.WHITE);
        }
    }

    private int parseSceneNumber(String value) {
        try {
            return Integer.parseInt(value.trim());
        } catch (NumberFormatException exception) {
            return 0;
        }
    }

    private void applyPreset(Bank bank, Preset preset) {
        Toast.makeText(
                this,
                "Preset selecionado: " + preset.name
                        + "\nBanco: " + bank.name
                        + "\nCena: " + preset.scene
                        + "\n" + preset.getActivePartsSummary()
                        + "\n\nMIDI será implementado na próxima fase.",
                Toast.LENGTH_LONG
        ).show();
    }

    private void saveBanks() {
        JSONArray banksArray = new JSONArray();

        try {
            for (Bank bank : banks) {
                JSONObject bankObject = new JSONObject();
                bankObject.put("name", bank.name);

                JSONArray presetsArray = new JSONArray();

                for (Preset preset : bank.presets) {
                    JSONObject presetObject = new JSONObject();
                    presetObject.put("name", preset.name);
                    presetObject.put("scene", preset.scene);

                    JSONArray statesArray = new JSONArray();

                    for (boolean partState : preset.partStates) {
                        statesArray.put(partState);
                    }

                    presetObject.put("partStates", statesArray);
                    presetsArray.put(presetObject);
                }

                bankObject.put("presets", presetsArray);
                banksArray.put(bankObject);
            }
        } catch (JSONException exception) {
            Toast.makeText(
                    this,
                    "Erro ao preparar os dados para salvar.",
                    Toast.LENGTH_SHORT
            ).show();
            return;
        }

        SharedPreferences preferences = getSharedPreferences(
                PREFS_NAME,
                Context.MODE_PRIVATE
        );

        preferences.edit()
                .putString(KEY_BANKS_JSON, banksArray.toString())
                .apply();
    }

    private void loadBanks() {
        banks.clear();

        SharedPreferences preferences = getSharedPreferences(
                PREFS_NAME,
                Context.MODE_PRIVATE
        );

        String savedJson = preferences.getString(KEY_BANKS_JSON, null);

        if (savedJson == null || savedJson.trim().isEmpty()) {
            createInitialExampleData();
            saveBanks();
            return;
        }

        try {
            JSONArray banksArray = new JSONArray(savedJson);

            for (int bankIndex = 0; bankIndex < banksArray.length(); bankIndex++) {
                JSONObject bankObject = banksArray.getJSONObject(bankIndex);

                String bankName = bankObject.optString(
                        "name",
                        "Banco sem nome"
                );

                Bank bank = new Bank(bankName);

                JSONArray presetsArray = bankObject.optJSONArray("presets");

                if (presetsArray != null) {
                    for (
                            int presetIndex = 0;
                            presetIndex < presetsArray.length();
                            presetIndex++
                    ) {
                        JSONObject presetObject =
                                presetsArray.getJSONObject(presetIndex);

                        String presetName = presetObject.optString(
                                "name",
                                "Preset sem nome"
                        );

                        int scene = presetObject.optInt("scene", 1);

                        boolean[] partStates = new boolean[8];
                        JSONArray statesArray =
                                presetObject.optJSONArray("partStates");

                        if (statesArray != null) {
                            for (
                                    int partIndex = 0;
                                    partIndex < 8 && partIndex < statesArray.length();
                                    partIndex++
                            ) {
                                partStates[partIndex] =
                                        statesArray.optBoolean(partIndex, false);
                            }
                        }

                        bank.presets.add(
                                new Preset(presetName, scene, partStates)
                        );
                    }
                }

                banks.add(bank);
            }
        } catch (JSONException exception) {
            banks.clear();
            createInitialExampleData();
            saveBanks();

            Toast.makeText(
                    this,
                    "Os dados salvos estavam inválidos. Exemplos foram restaurados.",
                    Toast.LENGTH_LONG
            ).show();
        }
    }

    private void createInitialExampleData() {
        Bank musicaA = new Bank("Música A");
        musicaA.presets.add(
                new Preset(
                        "Verso",
                        1,
                        new boolean[]{
                                true, false, true, false,
                                false, false, false, false
                        }
                )
        );
        musicaA.presets.add(
                new Preset(
                        "Refrão",
                        1,
                        new boolean[]{
                                true, true, true, false,
                                false, false, false, false
                        }
                )
        );

        Bank musicaB = new Bank("Música B");
        musicaB.presets.add(
                new Preset(
                        "Intro",
                        2,
                        new boolean[]{
                                false, true, false, true,
                                false, false, false, false
                        }
                )
        );
        musicaB.presets.add(
                new Preset(
                        "Ponte",
                        2,
                        new boolean[]{
                                true, true, false, false,
                                true, false, false, false
                        }
                )
        );

        banks.add(musicaA);
        banks.add(musicaB);
    }

    private TextView createText(
            String text,
            float textSize,
            boolean bold
    ) {
        TextView textView = new TextView(this);
        textView.setText(text);
        textView.setTextSize(textSize);

        if (bold) {
            textView.setTypeface(Typeface.DEFAULT_BOLD);
        }

        return textView;
    }

    private Button createButton(String text) {
        Button button = new Button(this);
        button.setText(text);
        button.setAllCaps(false);
        button.setTextSize(15);
        button.setGravity(Gravity.CENTER);
        return button;
    }

    private int dp(int value) {
        float density = getResources().getDisplayMetrics().density;
        return Math.round(value * density);
    }

    private static class Bank {
        private String name;
        private final List<Preset> presets = new ArrayList<>();

        Bank(String name) {
            this.name = name;
        }
    }

    private static class Preset {
        private String name;
        private int scene;
        private final boolean[] partStates;

        Preset(String name, int scene, boolean[] partStates) {
            this.name = name;
            this.scene = scene;
            this.partStates = new boolean[8];

            for (int i = 0; i < 8; i++) {
                if (i < partStates.length) {
                    this.partStates[i] = partStates[i];
                }
            }
        }

        String getActivePartsSummary() {
            StringBuilder result = new StringBuilder("Parts: ");

            boolean hasActivePart = false;

            for (int i = 0; i < partStates.length; i++) {
                if (partStates[i]) {
                    if (hasActivePart) {
                        result.append(", ");
                    }

                    result.append(i + 1);
                    hasActivePart = true;
                }
            }

            if (!hasActivePart) {
                result.append("nenhuma ativa");
            }

            return result.toString();
        }
    }
}