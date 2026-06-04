package com.example.myapplication.ui;

import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.View;
import android.view.animation.AlphaAnimation;
import android.widget.AdapterView;
import android.widget.ImageButton;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import com.example.myapplication.R;
import com.example.myapplication.network.ExchangeRateResponse;
import com.example.myapplication.repository.CurrencyRepository;
import com.example.myapplication.utils.NumberFormatter;
import com.google.android.material.textfield.TextInputEditText;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class ConverterActivity extends AppCompatActivity {
    private final SimpleDateFormat syncDateFormat = new SimpleDateFormat("dd.MM.yyyy HH:mm", Locale.getDefault());

    private Spinner spinnerFrom;
    private Spinner spinnerTo;
    private TextInputEditText amountFrom;
    private TextInputEditText amountTo;
    private TextView syncStatus;
    private TextView offlineBanner;
    private TextView rateDetails;
    private View resultSection;

    private CurrencyRepository repository;
    private List<CurrencyItem> currencies;
    private ExchangeRateResponse currentRates;
    private boolean isChangingText;
    private boolean isInitializing;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_main);

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main), (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
            return insets;
        });

        repository = CurrencyRepository.getInstance(this);
        bindViews();
        setupCurrencies();
        setupListeners();
        loadRates(false);
    }

    private void bindViews() {
        spinnerFrom = findViewById(R.id.spinner_from);
        spinnerTo = findViewById(R.id.spinner_to);
        amountFrom = findViewById(R.id.amount_from);
        amountTo = findViewById(R.id.amount_to);
        syncStatus = findViewById(R.id.sync_status);
        offlineBanner = findViewById(R.id.offline_banner);
        rateDetails = findViewById(R.id.rate_details);
        resultSection = findViewById(R.id.result_section);

        ImageButton refreshButton = findViewById(R.id.refresh_button);
        refreshButton.setOnClickListener(v -> loadRates(true));
    }

    private void setupCurrencies() {
        String[] codes = getResources().getStringArray(R.array.currency_codes);
        String[] names = getResources().getStringArray(R.array.currency_names);
        String[] flags = getResources().getStringArray(R.array.currency_flags);

        currencies = new ArrayList<>();
        for (int i = 0; i < codes.length; i++) {
            currencies.add(new CurrencyItem(codes[i], names[i], flags[i]));
        }

        CurrencySpinnerAdapter adapter = new CurrencySpinnerAdapter(this, currencies);
        isInitializing = true;
        spinnerFrom.setAdapter(adapter);
        spinnerTo.setAdapter(adapter);
        spinnerFrom.setSelection(0);
        spinnerTo.setSelection(1);
        isInitializing = false;
    }

    private void setupListeners() {
        amountFrom.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override public void afterTextChanged(Editable s) {}

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                if (!isChangingText) {
                    convertForward();
                }
            }
        });

        AdapterView.OnItemSelectedListener listener = new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                if (!isInitializing) {
                    if (parent == spinnerFrom) {
                        currentRates = null;
                        rateDetails.setText("Курс обновляется...");
                        loadRates(false);
                    } else {
                        convertForward();
                    }
                }
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {
            }
        };

        spinnerFrom.setOnItemSelectedListener(listener);
        spinnerTo.setOnItemSelectedListener(listener);
    }

    private void loadRates(boolean forceRefresh) {
        CurrencyItem base = selectedCurrency(spinnerFrom);
        syncStatus.setText(forceRefresh ? "Синхронизация: обновление..." : "Синхронизация: загрузка...");

        repository.loadRates(base.getCode(), forceRefresh, new CurrencyRepository.CurrencyCallback() {
            @Override
            public void onSuccess(ExchangeRateResponse response, boolean fromCache, long syncTime) {
                currentRates = response;
                offlineBanner.setVisibility(View.GONE);
                syncStatus.setText("Синхронизация: " + formatSyncTime(syncTime));
                convertForward();
            }

            @Override
            public void onOffline(ExchangeRateResponse cachedResponse, long syncTime) {
                currentRates = cachedResponse;
                offlineBanner.setText(getString(R.string.offline_prefix) + " " + formatSyncTime(syncTime));
                offlineBanner.setVisibility(View.VISIBLE);
                syncStatus.setText("Синхронизация: кэш");
                convertForward();
            }

            @Override
            public void onError(String message) {
                syncStatus.setText("Синхронизация: ошибка");
                Toast.makeText(ConverterActivity.this, message, Toast.LENGTH_LONG).show();
            }
        });
    }

    private void convertForward() {
        CurrencyItem base = selectedCurrency(spinnerFrom);
        CurrencyItem target = selectedCurrency(spinnerTo);
        Double amount = NumberFormatter.parseAmount(textOf(amountFrom));

        if (amount == null) {
            setResultText("");
            rateDetails.setText("Введите сумму для конвертации");
            return;
        }

        if (base.getCode().equals(target.getCode())) {
            setResultText(NumberFormatter.formatAmount(amount));
            rateDetails.setText("1 " + base.getCode() + " = 1 " + target.getCode());
            animateResult();
            return;
        }

        if (currentRates == null) {
            rateDetails.setText("Курс будет показан после загрузки данных");
            return;
        }

        Double rate = currentRates.getRate(target.getCode());
        if (rate == null || rate <= 0) {
            setResultText("N/A");
            rateDetails.setText("Курс " + target.getCode() + " недоступен");
            return;
        }

        double result = repository.convert(amount, rate);
        setResultText(NumberFormatter.formatAmount(result));
        rateDetails.setText("1 " + base.getCode() + " = " + NumberFormatter.formatRate(rate) + " " + target.getCode());
        animateResult();
    }

    private void setResultText(String value) {
        isChangingText = true;
        amountTo.setText(value);
        isChangingText = false;
    }

    private void animateResult() {
        resultSection.setBackgroundResource(R.drawable.bg_result_highlight);
        AlphaAnimation animation = new AlphaAnimation(0.35f, 1f);
        animation.setDuration(420);
        resultSection.startAnimation(animation);
        resultSection.postDelayed(() -> resultSection.setBackgroundResource(R.drawable.bg_result_normal), 520);
    }

    private CurrencyItem selectedCurrency(Spinner spinner) {
        return currencies.get(spinner.getSelectedItemPosition());
    }

    private String textOf(TextInputEditText editText) {
        return editText.getText() == null ? "" : editText.getText().toString();
    }

    private String formatSyncTime(long syncTime) {
        if (syncTime <= 0) {
            return "нет данных";
        }
        return syncDateFormat.format(new Date(syncTime));
    }
}
