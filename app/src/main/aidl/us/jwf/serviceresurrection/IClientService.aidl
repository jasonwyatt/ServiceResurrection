// IClientService.aidl
package us.jwf.serviceresurrection;

// Declare any non-default types here with import statements

interface IClientService {
    void kill();
    void bindToServer();
    void registerListenerWithServer();
    void sendEventToServer(String event);
    void unbindFromServer();
}
