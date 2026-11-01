package btools.routingapp;

import android.os.Bundle;

interface IBRouterService {
    String getTrackFromParams(in Bundle params);
}
