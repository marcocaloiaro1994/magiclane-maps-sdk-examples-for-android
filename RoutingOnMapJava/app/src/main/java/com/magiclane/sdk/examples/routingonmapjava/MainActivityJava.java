// -------------------------------------------------------------------------------------------------

/*
 * SPDX-FileCopyrightText: 2021-2026 Magic Lane International B.V. <info@magiclane.com>
 * SPDX-License-Identifier: Apache-2.0
 *
 * Contact Magic Lane at <info@magiclane.com> for SDK licensing options.
 */

// -------------------------------------------------------------------------------------------------

package com.magiclane.sdk.examples.routingonmapjava;

// -------------------------------------------------------------------------------------------------

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.Button;
import android.widget.ProgressBar;
import android.widget.TextView;

import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;

import com.magiclane.sdk.core.GemError;
import com.magiclane.sdk.core.GemSurfaceView;
import com.magiclane.sdk.core.SdkSettings;
import com.magiclane.sdk.d3scene.Animation;
import com.magiclane.sdk.d3scene.EAnimation;
import com.magiclane.sdk.d3scene.ERouteDisplayMode;
import com.magiclane.sdk.d3scene.MapView;
import com.magiclane.sdk.examples.routingonmapjava.databinding.ActivityMainJavaBinding;
import com.magiclane.sdk.places.Landmark;
import com.magiclane.sdk.routesandnavigation.Route;
import com.magiclane.sdk.routesandnavigation.RoutingService;
import com.magiclane.sdk.util.GemCall;
import com.magiclane.sdk.util.Util;
import com.google.android.material.bottomsheet.BottomSheetDialog;

import java.util.ArrayList;

// -------------------------------------------------------------------------------------------------

@SuppressWarnings("ALL")
public class MainActivityJava extends AppCompatActivity {
    private ActivityMainJavaBinding binding;
    private RoutingService routingService;
    private ArrayList<Route> routesList = new ArrayList<Route>();

    // ---------------------------------------------------------------------------------------------

    public MainActivityJava() {
        routingService = new RoutingService();

        routingService.setOnStarted(hasProgress ->
        {
            binding.progressBar.setVisibility(View.VISIBLE);
            return null;
        });

        routingService.setOnCompleted((routes, errorCode, hint) ->
        {
            binding.progressBar.setVisibility(View.GONE);

            switch (errorCode) {
                case GemError.NoError: {
                    routesList = routes;

                    GemCall.INSTANCE.execute(() ->
                    {
                        MapView mapView = binding.gemSurfaceView.getMapView();
                        if (mapView != null) {
                            Animation animation = new Animation(EAnimation.Linear, 1000, null, null);

                            mapView.presentRoutes(routes, null, true,
                                    true, true, true,
                                    true, true, animation,
                                    ERouteDisplayMode.Full, null);
                        }

                        return null;
                    });
                    break;
                }
                case GemError.Cancel: {
                    // The routing action was cancelled.
                    break;
                }
                default: {
                    // There was a problem at computing the routing operation.
                    showDialog("Routing service error: ${GemError.getMessage(errorCode)}");
                }
            }
            return null;
        });
    }

    // ---------------------------------------------------------------------------------------------

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        EdgeToEdge.enable(this);
        super.onCreate(savedInstanceState);
        binding = ActivityMainJavaBinding.inflate(this.getLayoutInflater());
        setContentView(binding.getRoot());

        SdkSettings.INSTANCE.setOnMapDataReady(isReady ->
        {
            if (!isReady)
                return null;

            // Defines an action that should be done when the world map is ready (Updated/ loaded).
            calculateRoute();

            // onTouch event callback
            binding.gemSurfaceView.getMapView().setOnTouch((xy ->
            {
                // xy are the coordinates of the touch event
                GemCall.INSTANCE.execute(() ->
                {
                    // tell the map view where the touch event happened
                    binding.gemSurfaceView.getMapView().setCursorScreenPosition(xy);

                    // get the visible routes at the touch event point
                    ArrayList<Route> routes = binding.gemSurfaceView.getMapView().getCursorSelectionRoutes();

                    // check if there is any route
                    if (routes != null && !routes.isEmpty()) {
                        // set the touched route as the main route and center on it
                        Route route = routes.get(0);

                        binding.gemSurfaceView.getMapView().getPreferences().getRoutes().setMainRoute(route);
                        binding.gemSurfaceView.getMapView().centerOnRoutes(routesList, ERouteDisplayMode.Full, null, new Animation(EAnimation.Linear, null, null, null));
                    }

                    return 0;
                });

                return null;
            }));

            return null;
        });

        SdkSettings.INSTANCE.setOnApiTokenRejected(() ->
        {
            /*
            The TOKEN you provided in the AndroidManifest.xml file was rejected.
            Make sure you provide the correct value, or if you don't have a TOKEN,
            check the magiclane.com website, sign up/sign in and generate one.
             */
            showDialog("TOKEN REJECTED");
            return null;
        });

        if (!Util.INSTANCE.isInternetConnected(this)) {
            showDialog("You must be connected to the internet!");
        }
    }

    // ---------------------------------------------------------------------------------------------


    private void calculateRoute() {
        GemCall.INSTANCE.execute(() ->
        {
            ArrayList<Landmark> waypoints = new ArrayList<>();
            waypoints.add(new Landmark("London", 51.5073204, -0.1276475));
            waypoints.add(new Landmark("Paris", 48.8566932, 2.3514616));

            routingService.calculateRoute(waypoints, null, false, null, null, null);
            return 0;
        });
    }

    // ---------------------------------------------------------------------------------------------

    private void showDialog(String text) {
        BottomSheetDialog dialog = new BottomSheetDialog(this);

        View view = getLayoutInflater().inflate(R.layout.dialog_layout, null);

        TextView title = view.findViewById(R.id.title);
        TextView message = view.findViewById(R.id.message);
        Button button = view.findViewById(R.id.button);

        title.setText(getString(R.string.error));
        message.setText(text);
        button.setOnClickListener(v -> dialog.dismiss());

        dialog.setCancelable(false);
        dialog.setContentView(view);
        dialog.show();
    }

    // ---------------------------------------------------------------------------------------------
}

// -------------------------------------------------------------------------------------------------
