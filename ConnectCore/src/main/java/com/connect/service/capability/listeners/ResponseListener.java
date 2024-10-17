package com.connect.service.capability.listeners;

public interface ResponseListener<T> extends ErrorListener {

    /**
     * Returns the success of the call of type T.
     * 
     * @param object Response object, can be any number of object types, depending on the protocol/capability/etc
     */
    void onSuccess(T object);
}
