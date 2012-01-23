/*
 * Androzic - android navigation client that uses OziExplorer maps (ozf2, ozfx3).
 * Copyright (C) 2010-2012  Andrey Novikov <http://andreynovikov.info/>
 *
 * This file is part of Androzic application.
 *
 * Androzic is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.

 * Androzic is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.

 * You should have received a copy of the GNU General Public License
 * along with Androzic.  If not, see <http://www.gnu.org/licenses/>.
 */

package com.androzic;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;

import android.app.Application;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.SharedPreferences.Editor;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.pm.PackageManager.NameNotFoundException;
import android.content.res.Configuration;
import android.graphics.Canvas;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.hardware.GeomagneticField;
import android.hardware.Sensor;
import android.hardware.SensorManager;
import android.location.Location;
import android.os.Environment;
import android.os.Handler;
import android.preference.PreferenceManager;
import android.util.DisplayMetrics;
import android.util.Log;
import android.view.WindowManager;
import android.widget.Toast;

import com.androzic.data.Route;
import com.androzic.data.Track;
import com.androzic.data.Waypoint;
import com.androzic.data.WaypointSet;
import com.androzic.map.Map;
import com.androzic.map.MapIndex;
import com.androzic.map.MockMap;
import com.androzic.map.online.OnlineMap;
import com.androzic.map.online.TileProvider;
import com.androzic.overlay.AccuracyOverlay;
import com.androzic.overlay.CurrentTrackOverlay;
import com.androzic.overlay.DistanceOverlay;
import com.androzic.overlay.LatLonGridOverlay;
import com.androzic.overlay.MapOverlay;
import com.androzic.overlay.NavigationOverlay;
import com.androzic.overlay.OtherGridOverlay;
import com.androzic.overlay.RouteOverlay;
import com.androzic.overlay.SharingOverlay;
import com.androzic.overlay.TrackOverlay;
import com.androzic.overlay.WaypointsOverlay;
import com.androzic.util.CSV;
import com.androzic.util.CoordinateParser;
import com.androzic.util.Geo;
import com.androzic.util.OziExplorerFiles;
import com.androzic.util.StringFormatter;
import com.androzic.util.Astro.Zenith;
import com.jhlabs.map.proj.ProjectionException;

public class Androzic extends Application
{
	public static final int PATH_WAYPOINTS = 0x001;
	public static final int PATH_TRACKS = 0x002;
	public static final int PATH_ROUTES = 0x004;
	public static final int PATH_ICONS = 0x008;
	
	public static final int ORDER_SHOW_PREFERENCE = 0;
	public static final int ORDER_DRAW_PREFERENCE = 1;
		
	public int coordinateFormat = 0;
	public int angleType = 0;
	public int sunriseType = 0;

	private List<TileProvider> onlineMaps;
	private OnlineMap onlineMap;
	private MapIndex maps;
	private List<Map> suitableMaps;
	private Map currentMap;
	private double[] location = new double[] {0.0, 0.0};
	private double magneticDeclination = 0;
	
	private List<Waypoint> waypoints = new ArrayList<Waypoint>();
	private List<WaypointSet> waypointSets = new ArrayList<WaypointSet>();
	private WaypointSet defWaypointSet;
	private List<Track> tracks = new ArrayList<Track>();
	private List<Route> routes = new ArrayList<Route>();
	
	public boolean centeredOn = false;
	public boolean hasCompass = false;
	private boolean memmsg = false;
	
	// FIXME Put overlays in separate class
	public LatLonGridOverlay llGridOverlay;
	public OtherGridOverlay grGridOverlay;
	public CurrentTrackOverlay currentTrackOverlay;
	public NavigationOverlay navigationOverlay;
	public WaypointsOverlay waypointsOverlay;
	public DistanceOverlay distanceOverlay;
	public AccuracyOverlay accuracyOverlay;
	public SharingOverlay sharingOverlay;
	public List<TrackOverlay> fileTrackOverlays = new ArrayList<TrackOverlay>();
	public List<RouteOverlay> routeOverlays = new ArrayList<RouteOverlay>();
	
	private Locale locale = null;
	private Handler handler = null;

	public String waypointPath;
	public String trackPath;
	public String routePath;
	private String rootPath;
	private String mapPath;
	public String iconPath;
	public boolean mapsInited = false;
	public MapActivity mapActivity;
	private int screenSize;
	public Drawable customCursor = null;
	public boolean iconsEnabled = false;
	public int iconX = 0;
	public int iconY = 0;
	
	public boolean isPaid = false;

	protected boolean mapGrid = false;
	protected boolean userGrid = false;
	protected int gridPrefer = 0;

    private static Androzic myself;

    public static Androzic getApplication()
    {
    	return myself;
    }
    
	protected void setMapActivity(MapActivity activity)
	{
		mapActivity = activity;
		for (MapOverlay mo : fileTrackOverlays)
		{
			mo.setMapContext(mapActivity);
		}
		if (currentTrackOverlay != null)
		{
			currentTrackOverlay.setMapContext(mapActivity);
		}
		for (MapOverlay mo : routeOverlays)
		{
			mo.setMapContext(mapActivity);
		}
		if (navigationOverlay != null)
		{
			navigationOverlay.setMapContext(mapActivity);
		}
		if (waypointsOverlay != null)
		{
			waypointsOverlay.setMapContext(mapActivity);
		}
		if (distanceOverlay != null)
		{
			distanceOverlay.setMapContext(mapActivity);
		}
		if (accuracyOverlay != null)
		{
			accuracyOverlay.setMapContext(mapActivity);
		}
		if (sharingOverlay != null)
		{
			sharingOverlay.setMapContext(mapActivity);
		}
		initGrids();
	}
	
	public List<MapOverlay> getOverlays(int order)
	{
		List<MapOverlay> overlays = new ArrayList<MapOverlay>();
		if (order == ORDER_DRAW_PREFERENCE)
		{
			if (llGridOverlay != null)
				overlays.add(llGridOverlay);
			if (grGridOverlay != null)
				overlays.add(grGridOverlay);
			if (accuracyOverlay != null)
				overlays.add(accuracyOverlay);
			overlays.addAll(fileTrackOverlays);
			if (currentTrackOverlay != null)
				overlays.add(currentTrackOverlay);
			overlays.addAll(routeOverlays);
			if (navigationOverlay != null)
				overlays.add(navigationOverlay);
			if (waypointsOverlay != null)
				overlays.add(waypointsOverlay);
			if (sharingOverlay != null)
				overlays.add(sharingOverlay);
			if (distanceOverlay != null)
				overlays.add(distanceOverlay);
		}
		else
		{
			if (accuracyOverlay != null)
				overlays.add(accuracyOverlay);
			if (distanceOverlay != null)
				overlays.add(distanceOverlay);
			if (navigationOverlay != null)
				overlays.add(navigationOverlay);
			if (currentTrackOverlay != null)
				overlays.add(currentTrackOverlay);
			overlays.addAll(routeOverlays);
			if (waypointsOverlay != null)
				overlays.add(waypointsOverlay);
			overlays.addAll(fileTrackOverlays);
			if (sharingOverlay != null)
				overlays.add(sharingOverlay);
			if (grGridOverlay != null)
				overlays.add(grGridOverlay);
			if (llGridOverlay != null)
				overlays.add(llGridOverlay);
		}
		return overlays;
	}
	
	protected void notifyOverlays()
	{
		if (mapActivity != null)
		{
			final List<MapOverlay> overlays = getOverlays(ORDER_SHOW_PREFERENCE);
			final boolean[] states = new boolean[overlays.size()];
			int i = 0;
	    	for (MapOverlay mo : overlays)
	    	{
	   			states[i] = mo.disable();
	   			i++;
	    	}
			mapActivity.executorThread.execute(new Runnable() {
				public void run()
				{
					int j = 0;
			    	for (MapOverlay mo : overlays)
			    	{
			   			mo.onMapChanged();
			   			if (states[j])
			   			{
			   				mo.enable();
			   			}
			   			if (mapActivity != null && mapActivity.map != null)
			   			{
			   				mapActivity.map.postInvalidate();
			   			}
			    	}
				}
			});
		}
	}
	
	public Zenith getZenith()
	{
		switch (sunriseType)
		{
			case 0:
				return Zenith.OFFICIAL;
			case 1:
				return Zenith.CIVIL;
			case 2:
				return Zenith.NAUTICAL;
			case 3:
				return Zenith.ASTRONOMICAL;
			default:
				return Zenith.OFFICIAL;
		}
	}

	public int addWaypoint(final Waypoint newWaypoint)
	{
		newWaypoint.set = defWaypointSet;
		waypoints.add(newWaypoint);
		return waypoints.lastIndexOf(newWaypoint);
	}

	public int addWaypoints(final List<Waypoint> newWaypoints)
	{
		if (newWaypoints != null)
		{
			for (Waypoint waypoint : newWaypoints)
				waypoint.set = defWaypointSet;
			waypoints.addAll(newWaypoints);
		}
		return waypoints.size() - 1;
	}

	public int addWaypoints(final List<Waypoint> newWaypoints, final WaypointSet waypointSet)
	{
		if (newWaypoints != null)
		{
			for (Waypoint waypoint : newWaypoints)
				waypoint.set = waypointSet;
			waypoints.addAll(newWaypoints);
			waypointSets.add(waypointSet);
		}
		return waypoints.size() - 1;
	}

	public boolean removeWaypoint(final Waypoint delWaypoint)
	{
		return waypoints.remove(delWaypoint);
	}
	
	public void removeWaypoint(final int delWaypoint)
	{
		waypoints.remove(delWaypoint);
	}

	public void clearWaypoints()
	{
		waypoints.clear();
	}
	
	public void clearDefaultWaypoints()
	{
		for (Iterator<Waypoint> iter = waypoints.iterator(); iter.hasNext();)
		{
			Waypoint wpt = iter.next();
			if (wpt.set == defWaypointSet)
			{
				iter.remove();
			}
		}	
	}
	
	public Waypoint getWaypoint(final int index)
	{
		return waypoints.get(index);
	}

	public int getWaypointIndex(Waypoint wpt)
	{
		return waypoints.indexOf(wpt);
	}

	public List<Waypoint> getWaypoints()
	{
		return waypoints;
	}

	public List<Waypoint> getWaypoints(WaypointSet set)
	{
		List<Waypoint> wpts = new ArrayList<Waypoint>();
		for (Waypoint wpt : waypoints)
		{
			if (wpt.set == set)
			{
				wpts.add(wpt);
			}
		}
		return wpts;
	}

	public List<Waypoint> getDefaultWaypoints()
	{
		return getWaypoints(defWaypointSet);
	}

	public boolean hasWaypoints()
	{
		return waypoints.size() > 0;
	}

	public void saveWaypoints(WaypointSet set)
	{
		try
		{
			String state = Environment.getExternalStorageState();
			if (! Environment.MEDIA_MOUNTED.equals(state))
				throw new FileNotFoundException(getString(R.string.err_nosdcard));
			
			File dir = new File(waypointPath);
			if (! dir.exists())
				dir.mkdirs();
			File file = new File(dir, "myWaypoints.wpt");
			if (! file.exists())
			{
				file.createNewFile();
			}
			if (file.canWrite())
			{
				OziExplorerFiles.saveWaypointsToFile(file, getWaypoints(set));
			}
		}
		catch (Exception e)
		{
			Toast.makeText(this, getString(R.string.err_sdwrite), Toast.LENGTH_LONG).show();
			Log.e("ANDROZIC", e.toString(), e);
		}
	}

	public void saveWaypoints()
	{
		for (WaypointSet wptset : waypointSets)
		{
			saveWaypoints(wptset);
		}
	}

	public void saveDefaultWaypoints()
	{
		saveWaypoints(defWaypointSet);
	}

	public void ensureVisible(Waypoint waypoint)
	{
		setLocation(waypoint.latitude, waypoint.longitude, false, true);
		centeredOn = true;
	}

	public void ensureVisible(double lat, double lon)
	{
		setLocation(lat, lon, false, true);
		centeredOn = true;
	}

	public int addWaypointSet(final WaypointSet newWaypointSet)
	{
		waypointSets.add(newWaypointSet);
		return waypointSets.lastIndexOf(newWaypointSet);
	}

	public List<WaypointSet> getWaypointSets()
	{
		return waypointSets;
	}

	public void removeWaypointSet(final int index)
	{
		if (index == 0)
			throw new IllegalArgumentException("Default waypoint set should be never removed");
		final WaypointSet wptset = waypointSets.remove(index);
		for (Iterator<Waypoint> iter = waypoints.iterator(); iter.hasNext();)
		{
			Waypoint wpt = iter.next();
			if (wpt.set == wptset)
			{
				iter.remove();
			}
		}
	}
	
	private void clearWaypointSets()
	{
		waypointSets.clear();
	}
	
	public int addTrack(final Track newTrack)
	{
		tracks.add(newTrack);
		return tracks.lastIndexOf(newTrack);
	}
	
	public boolean removeTrack(final Track delTrack)
	{
		delTrack.removed = true;
		return tracks.remove(delTrack);
	}
	
	public void clearTracks()
	{
		for (Track track : tracks)
		{
			track.removed = true;			
		}
		tracks.clear();
	}
	
	public Track getTrack(final int index)
	{
		return tracks.get(index);
	}

	public List<Track> getTracks()
	{
		return tracks;
	}

	public boolean hasTracks()
	{
		return tracks.size() > 0;
	}

	public Route trackToRoute2(Track track, float sensitivity) throws IllegalArgumentException
	{
		Route route = new Route();
		List<Track.TrackPoint> points = track.getPoints();
		Track.TrackPoint tp = points.get(0);
		route.addWaypoint("RWPT", tp.latitude, tp.longitude).proximity = 0;

		if (points.size() < 2)
			throw new IllegalArgumentException("Track too short");
		
		tp = points.get(points.size()-1);
		route.addWaypoint("RWPT", tp.latitude, tp.longitude).proximity = points.size()-1;
		
		int prx = Integer.parseInt(PreferenceManager.getDefaultSharedPreferences(this).getString(getString(R.string.pref_navigation_proximity), getString(R.string.def_navigation_proximity)));
		double proximity = prx * sensitivity;
		boolean peaks = true;
		int s = 1;
		
		while (peaks)
		{
			peaks = false;
			//Log.d("ANDROZIC", s+","+peaks);
			for (int i = s; i > 0; i--)
			{
				Waypoint sp = route.getWaypoint(i-1);
				Waypoint fp = route.getWaypoint(i);
				if (fp.silent)
					continue;
				double c = Geo.bearing(sp.latitude, sp.longitude, fp.latitude, fp.longitude);
				double xtkMin = 0, xtkMax = 0;
				int tpMin = 0, tpMax = 0;
				//Log.d("ANDROZIC", "vector: "+i+","+c);
				//Log.d("ANDROZIC", sp.name+"-"+fp.name+","+sp.proximity+"-"+fp.proximity);
				for (int j = sp.proximity; j < fp.proximity; j++)
				{
					tp = points.get(j);
					double b = Geo.bearing(tp.latitude, tp.longitude, fp.latitude, fp.longitude);
					double d = Geo.distance(tp.latitude, tp.longitude, fp.latitude, fp.longitude);
					double xtk = Geo.xtk(d, c, b);
					if (xtk != Double.NEGATIVE_INFINITY && xtk < xtkMin)
					{
						xtkMin = xtk;
						tpMin = j;
					}
					if (xtk != Double.NEGATIVE_INFINITY && xtk > xtkMax)
					{
						xtkMax = xtk;
						tpMax = j;
					}
				}
				// mark this vector to skip it on next pass
				if (xtkMin >= -proximity && xtkMax <= proximity)
				{
					fp.silent = true;
					continue;
				}
				if (xtkMin < -proximity)
				{
					tp = points.get(tpMin);
					route.insertWaypoint(i-1, "RWPT", tp.latitude, tp.longitude).proximity = tpMin;
					//Log.w("ANDROZIC", "min peak: "+s+","+tpMin+","+xtkMin);
					s++;
					peaks = true;
				}
				if (xtkMax > proximity)
				{
					tp = points.get(tpMax);
					int after = xtkMin < -proximity && tpMin < tpMax ? i : i-1;
					route.insertWaypoint(after, "RWPT", tp.latitude, tp.longitude).proximity = tpMax;
					//Log.w("ANDROZIC", "max peak: "+s+","+tpMax+","+xtkMax);
					s++;
					peaks = true;
				}
			}
			//Log.d("ANDROZIC", s+","+peaks);
			if (s > 500) peaks = false;
		}
		s = 0;
		for (Waypoint wpt : route.getWaypoints())
		{
			wpt.name += s;
			wpt.proximity = prx;
			wpt.silent = false;
			s++;
		}
		route.name = "RT_"+track.name;
		route.show = true;
		return route;
	}

	public Route trackToRoute(Track track, float sensitivity) throws IllegalArgumentException
	{
		Route route = new Route();
		List<Track.TrackPoint> points = track.getPoints();
		Track.TrackPoint lrp = points.get(0);
		route.addWaypoint("RWPT0", lrp.latitude, lrp.longitude);

		if (points.size() < 2)
			throw new IllegalArgumentException("Track too short");
		
		Track.TrackPoint cp = points.get(1);
		Track.TrackPoint lp = lrp;
		Track.TrackPoint tp = null;
		int i = 1;
		int prx = Integer.parseInt(PreferenceManager.getDefaultSharedPreferences(this).getString(getString(R.string.pref_navigation_proximity), getString(R.string.def_navigation_proximity)));
		double proximity = prx * sensitivity;
		double d = 0, t = 0, b, pb = 0, cb = -1, icb = 0, xtk = 0;
		
		while (i < points.size())
		{
			cp = points.get(i);
			d += Geo.distance(lp.latitude, lp.longitude, cp.latitude, cp.longitude);
			b = Geo.bearing(lp.latitude, lp.longitude, cp.latitude, cp.longitude);
			t += Geo.turn(pb, b);
			if (Math.abs(t) >= 360)
			{
				t = t - 360*Math.signum(t);
			}
			//Log.d("ANDROZIC", i+","+b+","+t);
			lp = cp;
			pb = b;
			i++;

			// calculate initial track
			if (cb < 0)
			{
				if (d > proximity)
				{
					cb = Geo.bearing(lrp.latitude, lrp.longitude, cp.latitude, cp.longitude);
					pb = cb;
					t = 0;
					icb = cb + 180;
					if (icb >= 360) icb -= 360;
					// Log.w("ANDROZIC", "Found vector:" + cb);
				}
				continue;
			}
			// find turn
			if (Math.abs(t) > 10)
			{
				if (tp == null)
				{
					tp = cp;
					// Log.w("ANDROZIC", "Found turn: "+i);
					continue;
				}
			}
			else if (tp != null && xtk < proximity / 10)
			{
				tp = null;
				xtk = 0;
				// Log.w("ANDROZIC", "Reset turn: "+i);
			}
			// if turn in progress check xtk
			if (tp != null)
			{
				double xd = Geo.distance(cp.latitude, cp.longitude, tp.latitude, tp.longitude);
				double xb = Geo.bearing(cp.latitude, cp.longitude, tp.latitude, tp.longitude);
				xtk = Geo.xtk(xd, icb, xb);
				// turned at sharp angle
				if (xtk == Double.NEGATIVE_INFINITY)
					xtk = Geo.xtk(xd, cb, xb);
				// Log.w("ANDROZIC", "XTK: "+xtk);
				if (Math.abs(xtk) > proximity * 3)
				{
					lrp = tp;
					route.addWaypoint("RWPT"+route.length(), lrp.latitude, lrp.longitude);
					cb = Geo.bearing(lrp.latitude, lrp.longitude, cp.latitude, cp.longitude);
					// Log.e("ANDROZIC", "Set WPT: "+(route.length()-1)+","+cb);
					pb = cb;
					t = 0;
					icb = cb + 180;
					if (icb >= 360) icb -= 360;
					tp = null;
					d = 0;
					xtk = 0;
				}
				continue;
			}
			// if still direct but pretty far away add a point
			if (d > proximity * 200)
			{
				lrp = cp;
				route.addWaypoint("RWPT"+route.length(), lrp.latitude, lrp.longitude);
				// Log.e("ANDROZIC", "Set WPT: "+(route.length()-1));
				d = 0;
			}
		}
		lrp = points.get(i-1);
		route.addWaypoint("RWPT"+route.length(), lrp.latitude, lrp.longitude);
		route.name = "RT_"+track.name;
		route.show = true;
		return route;
	}
	
	public int addRoute(final Route newRoute)
	{
		routes.add(newRoute);
		return routes.lastIndexOf(newRoute);
	}
	
	public boolean removeRoute(final Route delRoute)
	{
		delRoute.removed = true;
		return routes.remove(delRoute);
	}
	
	public void clearRoutes()
	{
		for (Route route : routes)
		{
			route.removed = true;
		}
		routes.clear();
	}
	
	public Route getRoute(final int index)
	{
		return routes.get(index);
	}
	
	public int getRouteIndex(final Route route)
	{
		return routes.indexOf(route);
	}
	
	public List<Route> getRoutes()
	{
		return routes;
	}

	public boolean hasRoutes()
	{
		return routes.size() > 0;
	}

	public double getDeclination()
	{
		if (angleType == 0)
		{
			GeomagneticField mag = new GeomagneticField((float) location[0], (float) location[1], 0.0f, System.currentTimeMillis());
			magneticDeclination = mag.getDeclination();
		}		
		return magneticDeclination;
	}
	
	public double fixDeclination(double declination)
	{
		if (angleType == 1)
		{
			declination += magneticDeclination;
			declination = (declination + 360.0) % 360.0;
		}
		return declination;
	}

	public double[] getLocation()
	{
		double[] res = new double[2];
		res[0] = location[0];
		res[1] = location[1];
		return res;
	}
	
	public Location getLocationAsLocation()
	{
		Location loc = new Location("fake");
		loc.setLatitude(location[0]);
		loc.setLongitude(location[1]);
		return loc;
	}
	
	public void initializeLocation()
	{
		double[] coordinate = null;
		SharedPreferences sharedPreferences = PreferenceManager.getDefaultSharedPreferences(this);
		boolean store = sharedPreferences.getBoolean(getString(R.string.pref_loc_store), getResources().getBoolean(R.bool.def_loc_store));
		if (store)
		{
			String loc = sharedPreferences.getString(getString(R.string.loc_last), null);
			if (loc != null)
			{
				coordinate = CoordinateParser.parse(loc);
				setLocation(coordinate[0], coordinate[1], true, true);
			}
		}
		
		if (coordinate == null)
		{
			setLocation(0, 0, true, true);
		}
	}
	
	public boolean setLocation(double lat, double lon, boolean findbest, boolean updatemag)
	{
		location[0] = lat;
		location[1] = lon;
		
		// TODO should honor altitude
		if (updatemag && angleType == 1)
		{
			GeomagneticField mag = new GeomagneticField((float) location[0], (float) location[1], 0.0f, System.currentTimeMillis());
			magneticDeclination = mag.getDeclination();
		}

		suitableMaps = maps.getMaps(lat, lon);
		boolean mapchanged = true;
		
		if (! findbest && currentMap != null)
		{
			if (currentMap.coversLatLon(lat, lon))
			{
				mapchanged = false;
			}
		}

		if (mapchanged)
		{
			Map newMap = null;
			if (suitableMaps.size() > 0)
			{
				newMap = suitableMaps.get(0);
			}
			if (newMap == null)
			{
				newMap = MockMap.getMap(lat, lon);
			}
			mapchanged = setMap(newMap);
		}
		
		return mapchanged;
	}
	
	public boolean scrollMap(int dx, int dy)
	{
		if (currentMap != null)
		{
			int[] xy = new int[2];
			double[] ll = new double[2];
			
			currentMap.getXYByLatLon(location[0], location[1], xy);
			currentMap.getLatLonByXY(xy[0] + dx, xy[1] + dy, ll);

			if (ll[0] > 90.0) ll[0] = 90.0;
			if (ll[0] < -90.0) ll[0] = -90.0;
			if (ll[1] > 180.0) ll[1] = 180.0;
			if (ll[1] < -180.0) ll[1] = -180.0;
			
			return setLocation(ll[0], ll[1], false, true);
		}
		return false;
	}

	public int[] getXYbyLatLon(double lat, double lon)
	{
		int[] xy = new int[] {0, 0};
		if (currentMap != null)
		{
			currentMap.getXYByLatLon(lat, lon, xy);
		}
		return xy;
	}
	
	public double getZoom()
	{
		if (currentMap != null)
			return currentMap.getZoom();
		else
			return 0.0;
	}
	
	public void zoomIn()
	{
		if (currentMap != null)
		{
			synchronized (currentMap)
			{
				double zoom = currentMap.getNextZoom();
				if (zoom > 0)
				{
					currentMap.setZoom(zoom);
					notifyOverlays();
				}
			}
		}
	}
	
	public void zoomOut()
	{
		if (currentMap != null)
		{
			synchronized (currentMap)
			{
				double zoom = currentMap.getPrevZoom();
				if (zoom > 0)
				{
					currentMap.setZoom(zoom);
					notifyOverlays();
				}
			}
		}
	}
	
	public double getNextZoom()
	{
		if (currentMap != null)
			return currentMap.getNextZoom();
		else
			return 0.0;
	}

	public double getPrevZoom()
	{
		if (currentMap != null)
			return currentMap.getPrevZoom();
		else
			return 0.0;
	}

	public void zoomBy(float factor)
	{
		if (currentMap != null)
		{
			synchronized (currentMap)
			{
				currentMap.zoomBy(factor);
			}
			notifyOverlays();
		}
	}

	public List<TileProvider> getOnlineMaps()
	{
		return onlineMaps;
	}

	public String getMapTitle()
	{
		if (currentMap != null)
			return currentMap.title;
		else
			return null;		
	}
	
	public Map getCurrentMap()
	{
		return currentMap;
	}
	
	public List<Map> getMaps()
	{
		return maps.getMaps();
	}
			
	public List<Map> getMaps(double[] loc)
	{
		return maps.getMaps(loc[0], loc[1]);
	}
	
	public int getNextMap()
	{
		if (currentMap != null)
		{
			int pos = suitableMaps.indexOf(currentMap);
			if (pos >= 0 && pos < suitableMaps.size()-1)
			{
				return suitableMaps.get(pos+1).id;
			}
		}
		else if (suitableMaps.size() > 0)
		{
			return suitableMaps.get(suitableMaps.size()-1).id;
		}
		return 0;
	}

	public int getPrevMap()
	{
		if (currentMap != null)
		{
			int pos = suitableMaps.indexOf(currentMap);
			if (pos > 0)
			{
				return suitableMaps.get(pos-1).id;
			}
		}
		else if (suitableMaps.size() > 0)
		{
			return suitableMaps.get(0).id;
		}
		return 0;
	}
	
	public boolean nextMap()
	{
		int id = getNextMap();
		if (id != 0)
			return selectMap(id);
		else
			return false;
	}

	public boolean prevMap()
	{
		int id = getPrevMap();
		if (id != 0)
			return selectMap(id);
		else
			return false;
	}

	public boolean selectMap(int id)
	{
		if (currentMap != null && currentMap.id == id)
			return false;
		
		Map newMap = null;
		for (Map map : suitableMaps)
		{
			if (map.id == id)
			{
				newMap = map;
				break;
			}
		}
		return setMap(newMap);
	}
	
	public boolean loadMap(int id)
	{
		Map newMap = null;
		for (Map map : maps.getMaps())
		{
			if (map.id == id)
			{
				newMap = map;
				break;
			}
		}
		boolean newmap = setMap(newMap);
		if (currentMap != null)
		{
			synchronized (currentMap)
			{
				int x = currentMap.getScaledWidth() / 2;
				int y = currentMap.getScaledHeight() / 2;
				currentMap.getLatLonByXY(x, y, location);
			}
			suitableMaps = maps.getMaps(location[0], location[1]);
		}
		return newmap;
	}

	protected void initGrids()
	{
		llGridOverlay = null;
		grGridOverlay = null;
		if (mapGrid && currentMap != null && currentMap.llGrid != null && currentMap.llGrid.enabled && mapActivity != null)
		{
			LatLonGridOverlay llgo = new LatLonGridOverlay(mapActivity);
			llgo.setGrid(currentMap.llGrid);
			llGridOverlay = llgo;
		}
		if (mapGrid && currentMap != null && currentMap.grGrid != null && currentMap.grGrid.enabled && mapActivity != null && (! userGrid || gridPrefer == 0))
		{
			OtherGridOverlay ogo = new OtherGridOverlay(mapActivity);
			ogo.setGrid(currentMap.grGrid);
			grGridOverlay = ogo;
		}
		else if (userGrid && currentMap != null && mapActivity != null)
		{
			SharedPreferences settings = PreferenceManager.getDefaultSharedPreferences(this);
			OtherGridOverlay ogo = new OtherGridOverlay(mapActivity);
			Map.Grid grid = currentMap.new Grid();
			grid.color1 = 0xFF0000FF;
			grid.color2 = 0xFF0000FF;
			grid.color3 = 0xFF0000FF;
			grid.enabled = true;
			grid.spacing = Integer.parseInt(settings.getString(getString(R.string.pref_grid_userscale), getResources().getString(R.string.def_grid_userscale)));
			int distanceIdx = Integer.parseInt(settings.getString(getString(R.string.pref_grid_userunit), "0"));
			grid.spacing *= Double.parseDouble(getResources().getStringArray(R.array.distance_factors_short)[distanceIdx]);
			grid.maxMPP = Integer.parseInt(settings.getString(getString(R.string.pref_grid_usermpp), getResources().getString(R.string.def_grid_usermpp)));
			ogo.setGrid(grid);
			grGridOverlay = ogo;
		}
	}
	
	private boolean setMap(final Map newMap)
	{
		// TODO should override equals()?
		if (newMap != null && ! newMap.equals(currentMap) && mapActivity != null)
		{
			Log.d("ANDROZIC", "Set map: " + newMap);
			try
			{
				newMap.activate(mapActivity.map, screenSize);
			}
			catch (final Exception e)
			{
				e.printStackTrace();
				handler.post(new Runnable() {
				    @Override
				    public void run() {
						Toast.makeText(Androzic.this, newMap.imagePath+": "+e.getMessage(), Toast.LENGTH_LONG).show();
				    }
				  });
				return false;
			}
			if (currentMap != null)
			{
				synchronized (currentMap)
				{
					currentMap.deactivate();
				}
			}
			currentMap = newMap;
			initGrids();
			notifyOverlays();
			return true;
		}
		return false;
	}
	
	public void setOnlineMap(String provider)
	{
		if (onlineMaps == null)
			return;
		for (TileProvider map : onlineMaps)
		{
			if (provider.equals(map.code))
			{
				boolean s = currentMap == onlineMap;					
				maps.removeMap(onlineMap);
				byte zoom = (byte) PreferenceManager.getDefaultSharedPreferences(this).getInt(getString(R.string.pref_onlinemapscale), getResources().getInteger(R.integer.def_onlinemapscale));
				onlineMap = new OnlineMap(map, zoom);
				maps.addMap(onlineMap);
				if (s)
					setMap(onlineMap);
			}
		}
	}
	
	public void drawMap(double[] loc, int[] lookAhead, int width, int height, Canvas c)
	{
		if (currentMap != null)
		{
			try
			{
				synchronized (currentMap)
				{
					currentMap.drawMap(loc, lookAhead, width, height, c);
				}
			}
			catch (OutOfMemoryError err)
			{
	        	if (! memmsg)
	        		Toast.makeText(this, R.string.err_nomemory, Toast.LENGTH_LONG).show();
	        	memmsg = true;
	        	err.printStackTrace();
			}
		}
	}
	
	public void clear()
	{
		clearRoutes();
		clearTracks();
		clearWaypoints();
		clearWaypointSets();
		SharedPreferences sharedPreferences = PreferenceManager.getDefaultSharedPreferences(this);
		boolean store = sharedPreferences.getBoolean(getString(R.string.pref_loc_store), getResources().getBoolean(R.bool.def_loc_store));
		if (store)
		{
			Editor editor = PreferenceManager.getDefaultSharedPreferences(this).edit();
			editor.putString(getString(R.string.loc_last), StringFormatter.coordinates(0, " ", location[0], location[1]));
			editor.commit();			
		}
		llGridOverlay = null;
		grGridOverlay = null;
		mapActivity = null;
		currentMap = null;
		suitableMaps = null;
		centeredOn = false;
		maps = null;
		mapsInited = false;
		memmsg = false;
	}

	public void initialize(MapState mapState)
	{
		waypoints.addAll(mapState.waypoints);
		tracks.addAll(mapState.tracks);
		routes.addAll(mapState.routes);
		for (Track track : tracks)
		{
			track.removed = false;
		}
		for (Route route : routes)
		{
			route.removed = false;
		}
		
		centeredOn = mapState.centeredOn;
		hasCompass = mapState.hasCompass;

		locale = mapState.locale;
		waypointPath = mapState.waypointPath;
		trackPath = mapState.trackPath;
		routePath = mapState.routePath;
		iconPath = mapState.iconPath;
		rootPath = mapState.rootPath;
		mapPath = mapState.mapPath;
		mapsInited = mapState.mapsInited;
		
		maps = mapState.maps;
		currentMap = mapState.currentMap;
		onlineMaps = mapState.onlineMaps;
		onlineMap = mapState.onlineMap;
		suitableMaps = mapState.suitableMaps;
		memmsg = mapState.memmsg;
		initGrids();
	}

	public void onRetainNonConfigurationInstance(MapState mapState)
	{
		mapState.waypoints.addAll(waypoints);
		mapState.tracks.addAll(tracks);
		mapState.routes.addAll(routes);

		mapState.centeredOn = centeredOn;
		mapState.hasCompass = hasCompass;
		
		mapState.locale = locale;
		mapState.waypointPath = waypointPath;
		mapState.trackPath = trackPath;
		mapState.routePath = routePath;
		mapState.iconPath = iconPath;
		mapState.rootPath = rootPath;
		mapState.mapPath = mapPath;
		mapState.mapsInited = mapsInited;
		
		mapState.maps = maps;
		mapState.currentMap = currentMap;
		mapState.onlineMaps.addAll(onlineMaps);
		mapState.onlineMap = onlineMap;
		mapState.suitableMaps.addAll(suitableMaps);
		mapState.memmsg = memmsg;
	}

	public void setRootPath(String path)
	{
		rootPath = path;
	}

	public String getRootPath()
	{
		return rootPath;
	}

	public void setDataPath(int pathtype, String path)
	{
		if ((pathtype & PATH_WAYPOINTS) > 0)
			waypointPath = rootPath + "/" + path;
		if ((pathtype & PATH_TRACKS) > 0)
			trackPath = rootPath + "/" + path;
		if ((pathtype & PATH_ROUTES) > 0)
			routePath = rootPath + "/" + path;
		if ((pathtype & PATH_ICONS) > 0)
			iconPath = rootPath + "/" + path;
	}

	public boolean setMapPath(String path)
	{
		String newPath = rootPath + "/" + path;
		if (mapPath == null || ! mapPath.equals(newPath))
		{
			mapPath = newPath;
			if (mapsInited)
			{
				resetMaps();
				return true;
			}
		}
		return false;
	}

	public String getMapPath()
	{
		return mapPath;
	}

	public void initializeMaps()
	{
		SharedPreferences settings = PreferenceManager.getDefaultSharedPreferences(this);
		boolean useIndex = settings.getBoolean(getString(R.string.pref_usemapindex), getResources().getBoolean(R.bool.def_usemapindex));
		maps = null;
		File index = new File(rootPath, "maps.idx");
		if (useIndex && index.exists())
		{
			try
			{
				FileInputStream fs = new FileInputStream(index);
				ObjectInputStream in = new ObjectInputStream(fs);
				maps = (MapIndex) in.readObject();
				in.close();
				int hash = MapIndex.getMapsHash(mapPath);
				if (hash != maps.hashCode())
				{
					maps = null;
				}
			}
			catch (IOException e)
			{
				e.printStackTrace();
			}
			catch (ClassNotFoundException e)
			{
				e.printStackTrace();
			}
		}
		if (maps == null)
		{
			maps = new MapIndex(mapPath);
			StringBuilder sb = new StringBuilder();
			for (Map mp : maps.getMaps())
			{
				if (mp.loadError != null)
				{
					String fn = new String(mp.mappath);
					if (fn.startsWith(mapPath))
					{
						fn = fn.substring(mapPath.length() + 1);
					}
					sb.append("<b>");
					sb.append(fn);
					sb.append(":</b> ");
					if (mp.loadError instanceof ProjectionException)
					{
						sb.append("projection error: ");					
					}
					sb.append(mp.loadError.getMessage());
					sb.append("<br />\n");
				}
			}
			if (sb.length() > 0)
			{
				maps.cleanBadMaps();
				startActivity(new Intent(this, ErrorDialog.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK).putExtra("title", getString(R.string.badmaps)).putExtra("message", sb.toString()));
			}
			
			if (useIndex)
			{
			    try
			    {
			    	FileOutputStream fs = new FileOutputStream(index);
			    	ObjectOutputStream out = new ObjectOutputStream(fs);
			    	out.writeObject(maps);
			    	out.close();
			    }
			    catch (IOException e)
			    {
			    	e.printStackTrace();
			    }
			}
		}

		onlineMaps = new ArrayList<TileProvider>();
		boolean useOnline = settings.getBoolean(getString(R.string.pref_useonlinemap), getResources().getBoolean(R.bool.def_useonlinemap));
		String current = settings.getString(getString(R.string.pref_onlinemap), getResources().getString(R.string.def_onlinemap));
		byte zoom = (byte) settings.getInt(getString(R.string.pref_onlinemapscale), getResources().getInteger(R.integer.def_onlinemapscale));
		TileProvider curProvider = null;
		String[] om = this.getResources().getStringArray(R.array.online_maps);
		for (String s : om)
		{
			TileProvider provider = TileProvider.fromString(s);
			if (provider != null)
			{
				onlineMaps.add(provider);
				if (current.equals(provider.code))
					curProvider = provider;
			}
		}
		File mapproviders = new File(rootPath, "providers.dat");
		if (isPaid && mapproviders.exists())
		{
			try
			{
				BufferedReader reader = new BufferedReader(new FileReader(mapproviders));
			    String line;
			    while ((line = reader.readLine()) != null)
				{
			    	line = line.trim();
			    	if (line.startsWith("#") || "".equals(line))
			    		continue;
					TileProvider provider = TileProvider.fromString(line);
					if (provider != null)
					{
						onlineMaps.add(provider);
					}
				}
			    reader.close();
			}
			catch (IOException e)
			{
				e.printStackTrace();
			}
		}
		if (useOnline && ! onlineMaps.isEmpty())
		{
			if (curProvider == null)
				curProvider = onlineMaps.get(0);
			onlineMap = new OnlineMap(curProvider, zoom);
			maps.addMap(onlineMap);
		}
		suitableMaps = maps.getMaps();
		mapsInited = true;
	}

	public void resetMaps()
	{
		File index = new File(rootPath, "maps.idx");
		if (index.exists())
			index.delete();
		initializeMaps();
	}

	void installData()
	{
		defWaypointSet = new WaypointSet(waypointPath, "myWaypoints");
		waypointSets.add(defWaypointSet);
		
		File icons = new File(iconPath, "icons.dat");
		if (icons.exists())
		{
			try
			{
				BufferedReader reader = new BufferedReader(new FileReader(icons));
			    String[] fields = CSV.parseLine(reader.readLine());
			    if (fields.length == 3)
			    {
			    	iconsEnabled = true;
			    	iconX = Integer.parseInt(fields[0]);
			    	iconY = Integer.parseInt(fields[1]);
			    }
			}
			catch (IOException e)
			{
				e.printStackTrace();
			}
		}

		
		File datums = new File(rootPath, "datums.dat");
		if (datums.exists())
		{
			try
			{
				OziExplorerFiles.loadDatums(datums);
			}
			catch (IOException e)
			{
				e.printStackTrace();
			}
		}
		File cursor = new File(rootPath, "cursor.png");
		if (cursor.exists())
		{
			try
			{
				customCursor = new BitmapDrawable(cursor.getAbsolutePath());
			}
			catch (Exception e)
			{
				e.printStackTrace();
			}
		}
		//installRawResource(R.raw.datums, "datums.xml");
	}

	void installRawResource(final int id, final String path)
	{
		try
		{
			// TODO Needs versioning
			openFileInput(path).close();
		}
		catch (Exception e)
		{

		}
		finally
		{
			InputStream in = getResources().openRawResource(id);
			FileOutputStream out = null;

			try
			{
				out = openFileOutput(path, MODE_PRIVATE);

				int size = in.available();

				byte[] buffer = new byte[size];
				in.read(buffer);
				in.close();

				out.write(buffer);
				out.close();

			}
			catch (Exception ex)
			{
			}
		}
	}

	@Override
	public void onConfigurationChanged(Configuration newConfig)
	{
		super.onConfigurationChanged(newConfig);
		if (locale != null)
		{
			newConfig.locale = locale;
		    Locale.setDefault(locale);
			getBaseContext().getResources().updateConfiguration(newConfig, getBaseContext().getResources().getDisplayMetrics());
		}
	}

	@Override
	public void onCreate()
	{
		super.onCreate();
		Log.e("ANDROZIC","Application onCreate()");

		myself = this;
		handler = new Handler();
		
        String intentToCheck = "com.androzic.donate";
        String myPackageName = getPackageName();
        PackageManager pm = getPackageManager();
        PackageInfo pi;
		try
		{
			pi = pm.getPackageInfo(intentToCheck, 0);
	        isPaid = (pm.checkSignatures(myPackageName, pi.packageName) == PackageManager.SIGNATURE_MATCH);
		}
		catch (NameNotFoundException e)
		{
		}

		File sdcard = Environment.getExternalStorageDirectory();
		Thread.setDefaultUncaughtExceptionHandler(new CrashHandler(this, sdcard.getAbsolutePath()));
		
		WindowManager wm = (WindowManager) getSystemService(Context.WINDOW_SERVICE);
		if (wm != null)
		{
			DisplayMetrics metrics = new DisplayMetrics();
			wm.getDefaultDisplay().getMetrics(metrics);
			screenSize = metrics.widthPixels * metrics.heightPixels;
		}
		else
		{
			screenSize = 320 * 480;
		}
		
		SensorManager sensorManager = (SensorManager) getSystemService(Context.SENSOR_SERVICE);
		if (sensorManager != null)
		{
			if (sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER) != null && sensorManager.getDefaultSensor(Sensor.TYPE_MAGNETIC_FIELD) != null)
			{
				hasCompass = true;
			}
		}
		sensorManager = null;
		
		SharedPreferences settings = PreferenceManager.getDefaultSharedPreferences(this);
		Configuration config = getBaseContext().getResources().getConfiguration();

		String lang = settings.getString(getString(R.string.pref_locale), "");
		if (! "".equals(lang) && ! config.locale.getLanguage().equals(lang))
		{
			locale = new Locale(lang);
		    Locale.setDefault(locale);
		    config.locale = locale;
		    getBaseContext().getResources().updateConfiguration(config, getBaseContext().getResources().getDisplayMetrics());
		}
	}	
}
