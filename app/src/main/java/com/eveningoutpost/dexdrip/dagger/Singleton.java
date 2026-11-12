package com.eveningoutpost.dexdrip.dagger;

import dagger.Lazy;


/**
 * Created by jamorham on 18/01/2018.
 *
 * Are you a lonely singleton object?
 *
 * Do you wish you could gain the advantages of lazy instantiation, shared resources with
 * flexible dependency injection, but without all that extra boiler plate code?
 *
 * Would you like to separate the concern of creating your objects but also keep things
 * as clear and readable as possible without multiple lines of annotations?
 *
 * What about avoiding the risk of failure to add interface methods for every child class
 * resulting in run-time null pointer exceptions that are not visible at compile time?
 *
 * If this sounds good to you, then come on in to the Singleton Hotel!
 *
 * This is the implementation class which handles a set of components
 *
 */

public class Singleton extends SingletonHotel {

    private Singleton() {
        super(Singleton.class);
        // inject from whatever components we are using
    }

    // call this method with the name you are looking for
    public static Object get(final String singleton) {

        final Singleton self = getSelf();
        // if you want to totally avoid reflection just add yourself to this switch statement
        // if you just want an easy life then don't bother and we'll look up and cache the result
        switch (singleton) {
            default:
                return ((Lazy) self.getObject(singleton)).get();
        }
    }

    // find ourself
    private static Singleton getSelf() {
        return WakeOnClassInit.instance;
    }

    // create ourself
    private static class WakeOnClassInit {
        static final Singleton instance = new Singleton();
    }

}
