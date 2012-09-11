/*
 *      Copyright (c) 2004-2012 YAMJ Members
 *      http://code.google.com/p/moviejukebox/people/list
 *
 *      Web: http://code.google.com/p/moviejukebox/
 *
 *      This software is licensed under a Creative Commons License
 *      See this page: http://code.google.com/p/moviejukebox/wiki/License
 *
 *      For any reuse or distribution, you must make clear to others the
 *      license terms of this work.
 */
package com.moviejukebox.scanner;

import com.moviejukebox.model.Movie;
import com.moviejukebox.plugin.trailer.ITrailerPlugin;
import com.moviejukebox.plugin.trailer.TrailerPlugin;
import com.moviejukebox.tools.PropertiesUtil;
import java.util.*;
import org.apache.log4j.Logger;

/**
 *
 * @author Stuart
 */
public class TrailerScanner {

    private static final Logger logger = Logger.getLogger(TrailerScanner.class);
    private static final String logMessage = "TrailerScanner: ";
    private static final long MILLIS_IN_DAY = 1000 * 60 * 60 * 24; // Milliseconds * Seconds * Minutes * Hours
    // Convert trailers.rescan.days from DAYS to MILLISECONDS for comparison purposes
    private static long trailersRescanDaysMillis = ((long) PropertiesUtil.getIntProperty("trailers.rescan.days", "15")) * MILLIS_IN_DAY;
    private static boolean trailersScannerEnable = PropertiesUtil.getBooleanProperty("trailers.scanner.enable", "true");
    private static String trailersScanner = PropertiesUtil.getProperty("trailers.scanner", "apple");
    private String trailerPluginList = Movie.UNKNOWN;
    private static final Map<String, ITrailerPlugin> trailerPlugins = Collections.synchronizedMap(new HashMap<String, ITrailerPlugin>());
    private static TrailerPlugin trailersPlugin = new TrailerPlugin();

    public TrailerScanner() {
        if (trailersScannerEnable) {
            synchronized (trailerPlugins) {
                ServiceLoader<ITrailerPlugin> trailerPluginsSet = ServiceLoader.load(ITrailerPlugin.class);

                for (ITrailerPlugin trailerPlugin : trailerPluginsSet) {
                    trailerPlugins.put(trailerPlugin.getName().toLowerCase().trim(), trailerPlugin);
                }

                trailerPluginList = getTrailerPluginList();
            }
        }
    }

    /**
     * This function will check movie trailers and return true if trailers needs to be re-scanned.
     *
     * @param movie
     * @return
     */
    public boolean isTrailersNeedRescan(Movie movie) {
        // Can the movie have trailers?
        if (!movie.canHaveTrailers()) {
            return Boolean.FALSE;
        }

        if (!trailersPlugin.isScanForTrailer(movie)) {
            logger.debug(logMessage + "Scanning skipped because " + movie.getBaseName() + " is not HD");
            return Boolean.FALSE;
        }

        // Does the trailer need to be overwritten?
        if (trailersPlugin.isOverwrite()) {
            return Boolean.TRUE;
        }

        // Check if this movie was already checked for trailers
        if (movie.isTrailerExchange()) {
            logger.debug(logMessage + "Movie " + movie.getTitle() + " has previously been checked for trailers, skipping.");
            return Boolean.FALSE;
        }

        // Check if we need to scan or rescan for trailers
        long now = new Date().getTime();
        if ((now - movie.getTrailerLastScan()) < trailersRescanDaysMillis) {
            return Boolean.FALSE;
        }

        return true;
    }

    public boolean getTrailers(Movie movie) {
        // Check if we need to scan at all
        if (!isTrailersNeedRescan(movie)) {
            return Boolean.FALSE;
        }

        boolean result = Boolean.FALSE;
        String trailersSearchToken;

        StringTokenizer st = new StringTokenizer(trailersScanner, ",");
        while (st.hasMoreTokens() && !result) {
            trailersSearchToken = st.nextToken();
            ITrailerPlugin trailerPlugin = trailerPlugins.get(trailersSearchToken);
            if (trailerPlugin == null) {
                logger.error(logMessage + "'" + trailersSearchToken + "' plugin doesn't exist, please check your moviejukebox properties. Valid plugins are : " + trailerPluginList);
            } else {
                result |= trailerPlugin.generate(movie);
            }
        }

        // Update trailerExchange
        if (result == Boolean.FALSE) {
            // Set trailerExchange to true if trailersRescanDaysMillis is < 0 (disable)
            result = trailersRescanDaysMillis < 0 ? Boolean.TRUE : Boolean.FALSE;
        }
        movie.setTrailerExchange(result);

        return result;
    }

    /**
     * Get a list of the trailer plugins
     *
     * @return
     */
    private static String getTrailerPluginList() {
        StringBuilder response = new StringBuilder();

        Set<String> keySet = trailerPlugins.keySet();
        for (String string : keySet) {
            response.append(string);
            response.append(Movie.SPACE_SLASH_SPACE);
        }

        response.delete(response.length() - 3, response.length());
        return response.toString();
    }
}
