package emed.tetra.ble.test;

import android.annotation.SuppressLint;
import android.app.Dialog;
import android.content.Context;
import android.graphics.Rect;
import android.os.Bundle;

import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodManager;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.TextView;

import androidx.fragment.app.DialogFragment;
import androidx.fragment.app.FragmentManager;

public class PairConnectorDialogFragment extends DialogFragment implements View.OnTouchListener, TextView.OnEditorActionListener {

    private View mFocusPuller;

    private EditText mPinView;

    private CheckBox mRememberConnectorView;

    private static final String TAG_PAIR_CONNECTOR_DIALOG_FRAGMENT = "PairConnectorDialogFragmentTag";

    public static PairConnectorDialogBuilder createBuilder(FragmentManager fragmentManager) {
        return new PairConnectorDialogBuilder(fragmentManager);
    }

    @Override
    public Dialog onCreateDialog(Bundle savedInstanceState) {
        // создаём диалоговое окно без дефолтного заголовка
        Dialog dialog = new Dialog(requireContext());
        if (dialog.getWindow() != null)
            dialog.getWindow().requestFeature(Window.FEATURE_NO_TITLE);
        // закомментировать, если нужно, чтобы диалог закрывался при клике вне его
        dialog.setCanceledOnTouchOutside(false);
        return dialog;
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_pair_connector_dialog, container, false);

        TextView tvTitle = view.findViewById(R.id.tvDialogTitle);
        tvTitle.setText(getString(R.string.request_pairing_connector_title));

        // добавить, если потребуется
        TextView tvSubTitle = view.findViewById(R.id.tvDialogSubTitle);
        tvSubTitle.setVisibility(View.GONE);

        mFocusPuller = view.findViewById(R.id.ltDialogContents);
        mFocusPuller.setOnTouchListener(this);

        mPinView = view.findViewById(R.id.etPairConnectorPin);
        mPinView.setOnEditorActionListener(this);

        mRememberConnectorView = view.findViewById(R.id.chbPairConnectorRemember);

        Button applyButton = view.findViewById(R.id.btnApply);
        applyButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                clearInputFocus(view);
                applyData();
            }
        });
        Button cancelButton = view.findViewById(R.id.btnCancel);
        cancelButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                clearInputFocus(view);
                cancelPairing();
            }
        });

        return view;
    }

    @Override
    public void onStart() {
        super.onStart();
        // подписываемся на получение событий
//        EventBus.getDefault().register(this);
    }

    @Override
    public void onStop() {
        super.onStop();
        // отписываемся от получения событий
//        EventBus.getDefault().unregister(this);
    }

    @SuppressLint("ClickableViewAccessibility")
    @Override
    public boolean onTouch(View v, MotionEvent event) {

        if (event.getAction() != MotionEvent.ACTION_DOWN || getDialog() == null)
            return false; // передаём дальше

        View focusedView = getDialog().getCurrentFocus();
        if (!(focusedView instanceof EditText))
            return false; // передаём дальше

        Rect outRect = new Rect();
        focusedView.getGlobalVisibleRect(outRect);
        if (!outRect.contains((int) event.getRawX(), (int) event.getRawY()))
            clearInputFocus(focusedView); // снимаем фокус ввода

        return false; // передаём дальше
    }

    @Override
    public boolean onEditorAction(TextView v, int actionId, KeyEvent e) {
        if (actionId == 105 || actionId == EditorInfo.IME_ACTION_DONE) {
            clearInputFocus(v); // снимаем фокус ввода
            return true; // обработано
        }
        return false; // передаём дальше
    }

    private void clearInputFocus(View view) {
        mFocusPuller.requestFocus();
        InputMethodManager imm = (InputMethodManager) view.getContext().getSystemService(Context.INPUT_METHOD_SERVICE);
        if (imm != null) // прячем клавиатуру
            imm.hideSoftInputFromWindow(view.getWindowToken(), 0);
    }

    private void applyData() {

        String pin = mPinView.getText().toString();
        boolean remember = mRememberConnectorView.isChecked();

        // отправляем pin коннектору и закрываем окно
        if(AppController.mBluetoothLeService != null)
            AppController.mBluetoothLeService.setPin(pin, remember);
        dismiss();
    }

    private void cancelPairing() {
        if(AppController.mBluetoothLeService != null)
            AppController.mBluetoothLeService.closeConnection(false);
        dismiss();
    }

//    @Subscribe(threadMode = ThreadMode.MAIN)
//    public void onControllerStateChangedEvent(ControllerStateChangedEvent event) {
//        // закрываем окно при отсутствии подключения
//        if (!event.isConnected()) {
//            clearInputFocus(mPinView);
//            dismiss();
//        }
//    }

    // класс строителя диалогового окна
    public static class PairConnectorDialogBuilder {

        private final FragmentManager mManager;

        public PairConnectorDialogBuilder(FragmentManager manager) {
            mManager = manager;
        }

        public void show() {
//            Class fragmentClass = PairConnectorDialogFragment.class;
//            ClassLoader classLoader = fragmentClass.getClassLoader();
//            if (classLoader == null) {
//                throw new IllegalStateException("Class loader is not available");
//            }
//            DialogFragment fragment = (DialogFragment) mManager.getFragmentFactory()
//                    .instantiate(classLoader, fragmentClass.getName());
//            fragment.show(mManager, TAG_PAIR_CONNECTOR_DIALOG_FRAGMENT);

            PairConnectorDialogFragment fragment = new PairConnectorDialogFragment();
            fragment.show(mManager, TAG_PAIR_CONNECTOR_DIALOG_FRAGMENT);
        }
    }
}
