package org.wikipedia.page;

import android.content.*;
import android.net.*;
import android.util.*;
import com.squareup.otto.*;
import org.json.*;
import org.wikipedia.*;
import org.wikipedia.events.*;
import org.wikipedia.history.*;

import android.preference.PreferenceManager;

/**
 * Handles any html links coming from a {@link org.wikipedia.page.PageViewFragment}
 */
public class LinkHandler implements CommunicationBridge.JSEventListener {
    private final Context context;
    private final CommunicationBridge bridge;
    private final Bus bus;
    private final Site currentSite;
    private WikipediaApp app;

    public LinkHandler(Context context, CommunicationBridge bridge, Site currentSite) {
        this.context = context;
        this.bridge = bridge;
        this.app = (WikipediaApp)context.getApplicationContext();
        this.bus = app.getBus();
        this.currentSite = currentSite;

        this.bridge.addListener("linkClicked", this);
    }

    private void handleExternalLink(final Uri uri) {
        if (WikipediaApp.isWikipediaZeroDevmodeOn() && WikipediaApp.getWikipediaZeroDisposition()) {
            SharedPreferences sharedPref = PreferenceManager.getDefaultSharedPreferences(context);
            if (sharedPref.getBoolean(WikipediaApp.PREFERENCE_ZERO_INTERSTITIAL, true)) {
                bus.post(new WikipediaZeroInterstitialEvent(uri));
            } else {
                visitExternalLink(uri);
            }
        } else {
            visitExternalLink(uri);
        }
    }

    private void visitExternalLink(Uri uri) {
        Intent intent = new Intent();
        intent.setAction(Intent.ACTION_VIEW);
        intent.setData(uri);
        context.startActivity(intent);
    }

    @Override
    public void onMessage(String messageType, JSONObject messagePayload) {
        try {
            String href = messagePayload.getString("href");
            if (href.startsWith("//")) {
                // That's a protocol specific link! Make it https!
                href = "https:" + href;
            }
            Log.d("Wikipedia", "Link clicked was " + href);
            if (href.startsWith("/wiki/")) {
                // TODO: Handle fragments
                PageTitle title = currentSite.titleForInternalLink(href);
                HistoryEntry historyEntry = new HistoryEntry(title, HistoryEntry.SOURCE_INTERNAL_LINK);
                bus.post(new NewWikiPageNavigationEvent(title, historyEntry));
            } else {
                Uri uri = Uri.parse(href);
                String authority = uri.getAuthority();
                // We don't want to handle /w/ URIs.
                // FIXME: Make this more complete, only to not handle URIs that contain unsupported actions
                if (authority != null && Site.isSupportedSite(authority) && uri.getPath().startsWith("/wiki/")) {
                    Site site = new Site(authority);
                    //TODO: Handle fragments
                    PageTitle title = site.titleForInternalLink(uri.getPath());
                    HistoryEntry historyEntry = new HistoryEntry(title, HistoryEntry.SOURCE_INTERNAL_LINK);
                    bus.post(new NewWikiPageNavigationEvent(title, historyEntry));
                } else {
                    handleExternalLink(uri);
                }
            }
        } catch (JSONException e) {
            throw new RuntimeException(e);
        }
    }
}
