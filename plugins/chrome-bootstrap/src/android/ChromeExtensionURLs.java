// Copyright (c) 2013 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package com.google.cordova;

import java.io.IOException;
import java.io.InputStream;
import java.io.ByteArrayInputStream;
import java.util.NavigableSet;
import java.util.TreeMap;

import org.apache.cordova.FileHelper;
import org.apache.cordova.api.CordovaPlugin;

import android.annotation.TargetApi;
import android.net.Uri;
import android.os.Build;
import android.webkit.WebResourceResponse;

public class ChromeExtensionURLs extends CordovaPlugin {

    @SuppressWarnings("unused")
    private static final String LOG_TAG = "ChromeExtensionURLs";
    // Plugins can register themselves to assist or modify the url decoding
    // We use a priority queue to enforce some order
    // Plugins are called in ascending order of priority to modify the url's and the response
    private static TreeMap<Integer, RequestModifyInterface> registeredPlugins =  new TreeMap<Integer, RequestModifyInterface>();

    public static interface RequestModifyInterface
    {
        public String modifyNewRequestUrl(String url);
        public InputStream modifyResponseInputStream(String url, InputStream is);
    }

    public static boolean registerInterfaceAtPriority(RequestModifyInterface plugin, int priority) {
        Integer priorityObj = priority;
        if(registeredPlugins.get(priorityObj) != null) {
            return false;
        }
        registeredPlugins.put(priorityObj, plugin);
        return true;
    }

    public static boolean unregisterInterfaceAtPriority(RequestModifyInterface plugin, int priority) {
        Integer priorityObj = priority;
        if(registeredPlugins.get(priorityObj) != plugin) {
            return false;
        }
        registeredPlugins.remove(priorityObj);
        return true;
    }

    @TargetApi(Build.VERSION_CODES.HONEYCOMB)
    @Override
    public WebResourceResponse shouldInterceptRequest(String url) {
        NavigableSet<Integer> pluginPrioritySet = registeredPlugins.navigableKeySet();
        for(Integer pluginPriority : pluginPrioritySet) {
            RequestModifyInterface plugin = registeredPlugins.get(pluginPriority);
            if(plugin != null) {
                url = plugin.modifyNewRequestUrl(url);
            }
        }

        String mimetype = FileHelper.getMimeType(url, this.cordova);
        String encoding = null;
        if (mimetype != null && mimetype.startsWith("text/")) {
            encoding = "UTF-8";
        }

        InputStream is = null;
        String filePath = Uri.parse(url).getPath();

        if ("/chrome-content-loaded".equals(filePath)) {
            is = new ByteArrayInputStream("Object.defineProperty(document, 'readyState', {get: function() { return 'loading'}, configurable: true });".getBytes());
        } else {
            try {
                is = this.cordova.getActivity().getAssets().open("www" + filePath);
            } catch (IOException ioe) {
                return null;
            }
        }

        for(Integer pluginPriority : pluginPrioritySet) {
            RequestModifyInterface plugin = registeredPlugins.get(pluginPriority);
            if(plugin != null) {
                is = plugin.modifyResponseInputStream(url, is);
            }
        }

        return new WebResourceResponse(mimetype, encoding, is);
    }
}
