package com.eveningoutpost.dexdrip;

import android.view.View;

import androidx.annotation.NonNull;
import androidx.databinding.BindingAdapter;


/**
 * Created by jamorham on 11/12/2017.
 */
public class BindingAdapterUtils {

    @BindingAdapter(value = {"showIfTrue"}, requireAll = true)
    public static void setShowIfTrue(@NonNull View view, boolean isVisible) {
        view.setVisibility(isVisible ? View.VISIBLE : View.GONE);
    }

    @BindingAdapter(value = {"invisibleIfFalse"}, requireAll = true)
    public static void setInvisibleIfFalse(@NonNull View view, boolean isVisible) {
        view.setVisibility(isVisible ? View.VISIBLE : View.INVISIBLE);
    }

}
