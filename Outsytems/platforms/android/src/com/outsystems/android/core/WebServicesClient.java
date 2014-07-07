/*
 * OutSystems Project
 * 
 * Copyright (C) 2014 OutSystems.
 * 
 * This software is proprietary.
 */
package com.outsystems.android.core;

import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.lang.reflect.Type;
import java.security.KeyManagementException;
import java.security.KeyStore;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.UnrecoverableKeyException;
import java.security.cert.CertificateException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

import org.apache.http.Header;
import org.apache.http.entity.StringEntity;

import android.util.Log;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import com.loopj.android.http.AsyncHttpClient;
import com.loopj.android.http.AsyncHttpResponseHandler;
import com.loopj.android.http.MySSLSocketFactory;
import com.loopj.android.http.RequestParams;
import com.outsystems.android.core.parsing.GenericResponseParsingTask;
import com.outsystems.android.helpers.HubManagerHelper;
import com.outsystems.android.model.Application;
import com.outsystems.android.model.Infrastructure;
import com.outsystems.android.model.Login;

/**
 * Class description.
 * 
 * @author <a href="mailto:vmfo@xpand-it.com">vmfo</a>
 * @version $Revision: 666 $
 * 
 */
public class WebServicesClient {

    public static final String URL_WEB_APPLICATION = "https://%1$s/%2$s";
    public static final String BASE_URL = "https://%1$s/OutSystemsAppService/";
    public static String DEMO_HOST_NAME = "apps8.outsystems.net";
    private static final String CONTENT_TYPE = "application/json";

    private static volatile WebServicesClient instance = null;
    private AsyncHttpClient client = null;

    private List<String> trustedHosts;

    // private constructor
    private WebServicesClient() {
        client = new AsyncHttpClient();
        client.addHeader("Content-Type", CONTENT_TYPE);

        trustedHosts = new ArrayList<String>();
        trustedHosts.add("outsystems.com");
        trustedHosts.add("outsystems.net");
        trustedHosts.add("outsystemscloud.com");
    }

    public static WebServicesClient getInstance() {
        if (instance == null) {
            synchronized (WebServicesClient.class) {
                instance = new WebServicesClient();
            }
        }
        return instance;
    }

    public static String getAbsoluteUrl(String hubApp, String relativeUrl) {
        return String.format(BASE_URL, hubApp) + relativeUrl + getApplicationServer();
    }

    public static String getAbsoluteUrlForImage(String hubApp, int idImage) {
        return String.format(BASE_URL, hubApp) + "applicationImage" + getApplicationServer() + "?id=" + idImage;
    }

    public static String getApplicationServer() {
        boolean jsfApplicationServer = HubManagerHelper.getInstance().isJSFApplicationServer();
        if (jsfApplicationServer) {
            return ".jsf";
        } else {
            return ".aspx";
        }
    }

    // post for content parameters
    private void post(String hubApp, String urlPath, HashMap<String, Object> parameters,
            AsyncHttpResponseHandler asyncHttpResponseHandler) {

        StringEntity entity = null;
        if (trustedHosts != null && hubApp != null) {
            for (String trustedHost : trustedHosts) {
                if (hubApp.contains(trustedHost)) {
                    client.setSSLSocketFactory(getSSLMySSLSocketFactory());
                    break;
                }
            }
        }
        client.post(null, getAbsoluteUrl(hubApp, urlPath), entity, CONTENT_TYPE, asyncHttpResponseHandler);
    }

    private void get(String hubApp, String urlPath, HashMap<String, String> parameters,
            AsyncHttpResponseHandler asyncHttpResponseHandler) {
        RequestParams params = null;
        if (parameters != null) {
            params = new RequestParams(parameters);
        }
        if (trustedHosts != null && hubApp != null) {
            for (String trustedHost : trustedHosts) {
                if (hubApp.contains(trustedHost)) {
                    client.setSSLSocketFactory(getSSLMySSLSocketFactory());
                    break;
                }
            }
        }
        client.get(getAbsoluteUrl(hubApp, urlPath), params, asyncHttpResponseHandler);
    }

    public void getInfrastructure(final String urlHubApp, final WSRequestHandler handler) {
        post(urlHubApp, "infrastructure", null, new AsyncHttpResponseHandler() {

            @Override
            public void onSuccess(final int statusCode, Header[] headers, final byte[] content) {
                if (statusCode != 200) {
                    handler.requestFinish(null, true, statusCode);
                } else {

                    new GenericResponseParsingTask() {
                        @Override
                        public Object parsingMethod() {

                            String contentString = "";
                            try {
                                contentString = new String(content, "UTF-8");
                            } catch (UnsupportedEncodingException e) {
                                Log.e("outsystems", e.toString());
                            }

                            Gson gson = new Gson();

                            Infrastructure infrastructure = gson.fromJson(contentString, Infrastructure.class);

                            return infrastructure;
                        }

                        @Override
                        public void parsingFinishMethod(Object result) {
                            handler.requestFinish(result, false, statusCode);
                        }
                    }.execute();

                }
            }

            @Override
            public void onFailure(int statusCode, Header[] headers, byte[] responseBody, Throwable error) {
                Log.d("outsystems", error.toString() + " " + statusCode);
                if (statusCode == 404 && !HubManagerHelper.getInstance().isJSFApplicationServer()) {
                    HubManagerHelper.getInstance().setJSFApplicationServer(true);
                    getInfrastructure(urlHubApp, handler);
                } else {
                    handler.requestFinish(null, true, statusCode);
                }
            }
        });
    }

    public void loginPlattform(final String username, final String password, final String device,
            final WSRequestHandler handler) {
        if (username == null || password == null) {
            handler.requestFinish(null, true, -1);
            return;
        }

        HashMap<String, String> param = new HashMap<String, String>();
        param.put("username", username);
        param.put("password", password);
        param.put("device", device);
        param.put("devicetype", "android");

        get(HubManagerHelper.getInstance().getApplicationHosted(), "login", param, new AsyncHttpResponseHandler() {

            @Override
            public void onFailure(int statusCode, Header[] headers, byte[] responseBody, Throwable arg3) {

            }

            @Override
            public void onSuccess(final int statusCode, Header[] headers, final byte[] content) {
                if (statusCode != 200) {
                    handler.requestFinish(null, true, statusCode);
                } else {
                    new GenericResponseParsingTask() {
                        @Override
                        public Object parsingMethod() {
                            String contentString = "";
                            try {
                                contentString = new String(content, "UTF-8");
                            } catch (UnsupportedEncodingException e) {
                                Log.e("outsystems", e.toString());
                            }

                            Gson gson = new Gson();
                            Login login = gson.fromJson(contentString, Login.class);

                            return login;
                        }

                        @Override
                        public void parsingFinishMethod(Object result) {
                            if (statusCode == 404 && !HubManagerHelper.getInstance().isJSFApplicationServer()) {
                                HubManagerHelper.getInstance().setJSFApplicationServer(true);
                                loginPlattform(username, password, device, handler);
                            } else {
                                handler.requestFinish(result, false, statusCode);
                            }

                        }
                    }.execute();
                }
            }
        });
    }

    public void registerToken(final String device, final WSRequestHandler handler) {
        if (device == null) {
            handler.requestFinish(null, true, -1);
            return;
        }

        HashMap<String, String> param = new HashMap<String, String>();
        param.put("device", device);
        param.put("devicetype", "android");
        
        get(HubManagerHelper.getInstance().getApplicationHosted(), "registertoken", param,
                new AsyncHttpResponseHandler() {

                    @Override
                    public void onFailure(int statusCode, Header[] headers, byte[] responseBody, Throwable arg3) {
                        handler.requestFinish(null, true, statusCode);
                    }

                    @Override
                    public void onSuccess(final int statusCode, Header[] headers, final byte[] content) {
                        if (statusCode != 200) {
                            handler.requestFinish(null, true, statusCode);
                        } else {
                            handler.requestFinish(null, false, statusCode);
                        }
                    }
                });
    }

    public void getApplications(final String urlHubApp, final WSRequestHandler handler) {
        post(urlHubApp, "applications", null, new AsyncHttpResponseHandler() {

            @Override
            public void onSuccess(final int statusCode, Header[] headers, final byte[] content) {
                if (statusCode != 200) {
                    handler.requestFinish(null, true, statusCode);
                } else {

                    new GenericResponseParsingTask() {
                        @Override
                        public Object parsingMethod() {

                            String contentString = "";
                            try {
                                contentString = new String(content, "UTF-8");
                            } catch (UnsupportedEncodingException e) {
                                Log.e("outsystems", e.toString());
                            }

                            Gson gson = new Gson();
                            Type collectionType = new TypeToken<List<Application>>() {
                            }.getType();
                            List<Application> applications = gson.fromJson(contentString, collectionType);

                            return applications;
                        }

                        @Override
                        public void parsingFinishMethod(Object result) {
                            handler.requestFinish(result, false, statusCode);
                        }
                    }.execute();

                }
            }

            @Override
            public void onFailure(int statusCode, Header[] headers, byte[] responseBody, Throwable error) {
                Log.d("outsystems", error.toString() + " " + statusCode);
                if (statusCode == 404 && !HubManagerHelper.getInstance().isJSFApplicationServer()) {
                    HubManagerHelper.getInstance().setJSFApplicationServer(true);
                    getApplications(urlHubApp, handler);
                } else {
                    handler.requestFinish(null, true, statusCode);
                }
            }
        });
    }

    private MySSLSocketFactory getSSLMySSLSocketFactory() {
        KeyStore trustStore = null;
        try {
            trustStore = KeyStore.getInstance(KeyStore.getDefaultType());
            trustStore.load(null, null);
        } catch (NoSuchAlgorithmException e) {
            Log.e("outsystems", e.toString());
        } catch (CertificateException e) {
            Log.e("outsystems", e.toString());
        } catch (IOException e) {
            Log.e("outsystems", e.toString());
        } catch (KeyStoreException e1) {
            Log.e("outsystems", e1.toString());
        }
        MySSLSocketFactory sf = null;
        try {
            sf = new MySSLSocketFactory(trustStore);
        } catch (KeyManagementException e) {
            Log.e("outsystems", e.toString());
        } catch (UnrecoverableKeyException e) {
            Log.e("outsystems", e.toString());
        } catch (NoSuchAlgorithmException e) {
            Log.e("outsystems", e.toString());
        } catch (KeyStoreException e) {
            Log.e("outsystems", e.toString());
        }
        sf.setHostnameVerifier(MySSLSocketFactory.ALLOW_ALL_HOSTNAME_VERIFIER);
        return sf;
    }
}