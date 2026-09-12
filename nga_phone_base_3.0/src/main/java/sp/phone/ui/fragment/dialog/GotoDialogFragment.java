package sp.phone.ui.fragment.dialog;

import android.app.Activity;
import android.app.Dialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.NumberPicker;
import android.widget.RadioGroup;

import androidx.annotation.IdRes;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;

import butterknife.BindView;
import butterknife.ButterKnife;
import gov.anzong.androidnga.R;
import sp.phone.util.ActivityUtils;

public class GotoDialogFragment extends NoframeDialogFragment {

    @BindView(R.id.numberPicker)
    public NumberPicker mNumberPicker;

    @BindView(R.id.radioGroup)
    public RadioGroup mRadioGroup;

    private int mMaxFloor;

    private int mMaxPage;
    private boolean mCanPage;
    private boolean mCanFloor;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        Bundle bundle = getArguments();
        mMaxPage = bundle.getInt("page", 1);
        mMaxFloor = bundle.getInt("floor_max", Math.max(0, bundle.getInt("floor", 0) - 1));
        mCanPage = bundle.getBoolean("can_page", mMaxPage > 0);
        mCanFloor = bundle.getBoolean("can_floor", mMaxFloor > 0);
        super.onCreate(savedInstanceState);
    }

    @NonNull
    @Override
    public Dialog onCreateDialog(Bundle savedInstanceState) {
        return new AlertDialog.Builder(getContext())
                .setTitle(R.string.goto_floor_description)
                .setView(getDialogView())
                .setPositiveButton(android.R.string.ok, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        mNumberPicker.clearFocus();
                        Intent intent = new Intent();
                        dismiss();
                        if (mRadioGroup.getCheckedRadioButtonId() == R.id.page) {
                            intent.putExtra("page", mNumberPicker.getValue());
                        } else {
                            intent.putExtra("floor", mNumberPicker.getValue());
                        }
                        getTargetFragment().onActivityResult(ActivityUtils.REQUEST_CODE_JUMP_PAGE, Activity.RESULT_OK, intent);
                    }
                })
                .setNegativeButton(android.R.string.cancel, null)
                .create();
    }

    private View getDialogView() {
        View view = getActivity().getLayoutInflater().inflate(R.layout.dialog_page_picker, null, false);
        ButterKnife.bind(this, view);
        mRadioGroup.setOnCheckedChangeListener(new RadioGroup.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(RadioGroup group, @IdRes int checkedId) {
                if (checkedId == R.id.page) {
                    initPagePicker();
                } else {
                    initFloorPicker();
                }
            }
        });
        view.findViewById(R.id.page).setEnabled(mCanPage);
        view.findViewById(R.id.floor).setEnabled(mCanFloor);
        mRadioGroup.check(mCanPage ? R.id.page : R.id.floor);
        if (mCanPage) initPagePicker(); else initFloorPicker();
        return view;
    }

    private void initPagePicker() {
        mNumberPicker.setMinValue(0);
        mNumberPicker.setMaxValue(Math.max(1, mMaxPage));
        mNumberPicker.setMinValue(1);
        mNumberPicker.setValue(Math.max(1, mMaxPage));
    }

    public void initFloorPicker() {
        mNumberPicker.setMinValue(0);
        mNumberPicker.setMaxValue(Math.max(0, mMaxFloor));
        mNumberPicker.setValue(Math.max(0, mMaxFloor));
    }

}
