package com.eveningoutpost.dexdrip.profileeditor;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.annotations.Expose;


/**
 * Created by jamorham on 21/06/2016.
 */
public class ProfileItem implements Comparable<ProfileItem>{

    private String title;
    @Expose
    public int start_min, end_min;
    @Expose
    public double carb_ratio, sensitivity, absorption_rate;


    public ProfileItem(int start_min, int end_min,double carb_ratio,double sensitivity)
    {
        this.start_min = start_min;
        this.end_min = end_min;
        this.carb_ratio = carb_ratio;
        this.sensitivity = sensitivity;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String name) {
        this.title = name;
    }

    public ProfileItem clone()
    {
        return new ProfileItem(this.start_min, this.end_min, this.carb_ratio, this.sensitivity);
    }

    public String toJson() {
        Gson gson = new GsonBuilder()
                .excludeFieldsWithoutExposeAnnotation()
                //.registerTypeAdapter(Date.class, new DateTypeAdapter())
                .serializeSpecialFloatingPointValues()
                .create();
        return gson.toJson(this);
    }

    @Override
    public int compareTo(ProfileItem o) {
            final Integer myStartMin = start_min;
            final Integer oStartMin = o.end_min;
            return myStartMin.compareTo(oStartMin);
        }

    @Override
    public boolean equals(final Object o) {
        if (o == this) {
            return true;
        }
        if (!(o instanceof ProfileItem)) {
            return false;
        }
        final ProfileItem pi = (ProfileItem) o;

        // Note: is only testing equality of values
        return carb_ratio == pi.carb_ratio
                && sensitivity == pi.sensitivity
                && absorption_rate == pi.absorption_rate;
    }
}
