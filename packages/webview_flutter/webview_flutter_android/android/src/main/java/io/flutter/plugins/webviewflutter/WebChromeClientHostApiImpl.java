// Copyright 2013 The Flutter Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package io.flutter.plugins.webviewflutter;

import android.os.Build;
import android.os.Message;
import android.webkit.ConsoleMessage;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceRequest;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.RequiresApi;
import androidx.annotation.VisibleForTesting;
import io.flutter.plugins.webviewflutter.GeneratedAndroidWebView.WebChromeClientHostApi;
import android.webkit.ValueCallback;
import io.flutter.plugins.webviewflutter.FileChooserLauncher;
import android.net.Uri;
import android.content.Context;
/**
 * Host api implementation for {@link WebChromeClient}.
 *
 * <p>Handles creating {@link WebChromeClient}s that intercommunicate with a paired Dart object.
 */
public class WebChromeClientHostApiImpl implements WebChromeClientHostApi {
  private final InstanceManager instanceManager;
  private final WebChromeClientCreator webChromeClientCreator;
  private final WebChromeClientFlutterApiImpl flutterApi;

  /**
   * Implementation of {@link WebChromeClient} that passes arguments of callback methods to Dart.
   */
  public static class WebChromeClientImpl extends WebChromeClient implements Releasable {
    @Nullable private WebChromeClientFlutterApiImpl flutterApi;
    private WebViewClient webViewClient;

    /**
     * Creates a {@link WebChromeClient} that passes arguments of callbacks methods to Dart.
     *
     * @param flutterApi handles sending messages to Dart
     * @param webViewClient receives forwarded calls from {@link WebChromeClient#onCreateWindow}
     */
    public WebChromeClientImpl(
        @NonNull WebChromeClientFlutterApiImpl flutterApi, WebViewClient webViewClient) {
      this.flutterApi = flutterApi;
      this.webViewClient = webViewClient;
    }
    
     @Override
    public boolean onConsoleMessage(ConsoleMessage consoleMessage) {
        android.util.Log.d("android webview", consoleMessage.message());
        return true;
    }
    
   
    @Override
    public boolean onShowFileChooser(
        WebView webView,
        ValueCallback<Uri[]> filePathCallback,
        FileChooserParams fileChooserParams) {
      // info as of 2021-03-08:
      // don't use fileChooserParams.getTitle() as it is (always? on Mi 9T Pro Android 10 at least) null
      // don't use fileChooserParams.isCaptureEnabled() as it is (always? on Mi 9T Pro Android 10 at least) false, even when the file upload allows images or any file
      final Context context = webView.getContext();
      final boolean allowMultipleFiles =
          Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP
              && fileChooserParams.getMode() == FileChooserParams.MODE_OPEN_MULTIPLE;
      final String[] acceptTypes =
          Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP
              ? fileChooserParams.getAcceptTypes()
              : new String[0];
      new FileChooserLauncher(context, allowMultipleFiles, filePathCallback, acceptTypes).start();
      return true;
    }

    @Override
    public boolean onCreateWindow(
        final WebView view, boolean isDialog, boolean isUserGesture, Message resultMsg) {
      return onCreateWindow(view, resultMsg, new WebView(view.getContext()));
    }

    /**
     * Verifies that a url opened by `Window.open` has a secure url.
     *
     * @param view the WebView from which the request for a new window originated.
     * @param resultMsg the message to send when once a new WebView has been created. resultMsg.obj
     *     is a {@link WebView.WebViewTransport} object. This should be used to transport the new
     *     WebView, by calling WebView.WebViewTransport.setWebView(WebView)
     * @param onCreateWindowWebView the temporary WebView used to verify the url is secure
     * @return this method should return true if the host application will create a new window, in
     *     which case resultMsg should be sent to its target. Otherwise, this method should return
     *     false. Returning false from this method but also sending resultMsg will result in
     *     undefined behavior
     */
    @VisibleForTesting
    boolean onCreateWindow(
        final WebView view, Message resultMsg, @Nullable WebView onCreateWindowWebView) {
      final WebViewClient windowWebViewClient =
          new WebViewClient() {
            @RequiresApi(api = Build.VERSION_CODES.N)
            @Override
            public boolean shouldOverrideUrlLoading(
                @NonNull WebView windowWebView, @NonNull WebResourceRequest request) {
              if (!webViewClient.shouldOverrideUrlLoading(view, request)) {
                view.loadUrl(request.getUrl().toString());
              }
              return true;
            }

            @Override
            public boolean shouldOverrideUrlLoading(WebView windowWebView, String url) {
              if (!webViewClient.shouldOverrideUrlLoading(view, url)) {
                view.loadUrl(url);
              }
              return true;
            }
      
          };

      if (onCreateWindowWebView == null) {
        onCreateWindowWebView = new WebView(view.getContext());
      }
      onCreateWindowWebView.setWebViewClient(windowWebViewClient);

      final WebView.WebViewTransport transport = (WebView.WebViewTransport) resultMsg.obj;
      transport.setWebView(onCreateWindowWebView);
      resultMsg.sendToTarget();

      return true;
    }

    @Override
    public void onProgressChanged(WebView view, int progress) {
      if (flutterApi != null) {
        flutterApi.onProgressChanged(this, view, (long) progress, reply -> {});
      }
    }

    /**
     * Set the {@link WebViewClient} that calls to {@link WebChromeClient#onCreateWindow} are passed
     * to.
     *
     * @param webViewClient the forwarding {@link WebViewClient}
     */
    public void setWebViewClient(WebViewClient webViewClient) {
      this.webViewClient = webViewClient;
    }

    @Override
    public void release() {
      if (flutterApi != null) {
        flutterApi.dispose(this, reply -> {});
      }
      flutterApi = null;
    }
  }

  /** Handles creating {@link WebChromeClient}s for a {@link WebChromeClientHostApiImpl}. */
  public static class WebChromeClientCreator {
    /**
     * Creates a {@link DownloadListenerHostApiImpl.DownloadListenerImpl}.
     *
     * @param flutterApi handles sending messages to Dart
     * @param webViewClient receives forwarded calls from {@link WebChromeClient#onCreateWindow}
     * @return the created {@link DownloadListenerHostApiImpl.DownloadListenerImpl}
     */
    public WebChromeClientImpl createWebChromeClient(
        WebChromeClientFlutterApiImpl flutterApi, WebViewClient webViewClient) {
      return new WebChromeClientImpl(flutterApi, webViewClient);
    }
  }

  /**
   * Creates a host API that handles creating {@link WebChromeClient}s.
   *
   * @param instanceManager maintains instances stored to communicate with Dart objects
   * @param webChromeClientCreator handles creating {@link WebChromeClient}s
   * @param flutterApi handles sending messages to Dart
   */
  public WebChromeClientHostApiImpl(
      InstanceManager instanceManager,
      WebChromeClientCreator webChromeClientCreator,
      WebChromeClientFlutterApiImpl flutterApi) {
    this.instanceManager = instanceManager;
    this.webChromeClientCreator = webChromeClientCreator;
    this.flutterApi = flutterApi;
  }

  @Override
  public void create(Long instanceId, Long webViewClientInstanceId) {
    final WebViewClient webViewClient =
        (WebViewClient) instanceManager.getInstance(webViewClientInstanceId);
    final WebChromeClient webChromeClient =
        webChromeClientCreator.createWebChromeClient(flutterApi, webViewClient);
    instanceManager.addInstance(webChromeClient, instanceId);
  }
}
