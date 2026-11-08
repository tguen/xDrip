package com.eveningoutpost.dexdrip.ui.helpers;

import androidx.annotation.ColorInt;
import androidx.core.graphics.ColorUtils;


public class ColorUtil {

    // originally from https://stackoverflow.com/a/17490633 with extra wrapper methods added by jamorham

    public static int blendColor(@ColorInt int color1, @ColorInt int color2, float ratio) {
        return ColorUtils.blendARGB(color1, color2, ratio);
    }

}
