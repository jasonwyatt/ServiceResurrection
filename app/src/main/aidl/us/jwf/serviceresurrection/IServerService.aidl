package us.jwf.serviceresurrection;

import us.jwf.serviceresurrection.IListener;

interface IServerService {
    int addListener(in IListener listener);
    void sendEvent(int id, String event);
}
