// IWebSocketInterface.aidl
package ru.ku.yfrsmartweight;

// Declare any non-default types here with import statements

interface IWebSocketInterface {
    void bindListener(IBinder callback);
    oneway void sendMSG(String message);

}
