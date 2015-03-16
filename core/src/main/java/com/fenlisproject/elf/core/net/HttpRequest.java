/*
* Copyright (C) 2015 Steven Lewi
*
* Licensed under the Apache License, Version 2.0 (the "License");
* you may not use this file except in compliance with the License.
* You may obtain a copy of the License at
*
*      http://www.apache.org/licenses/LICENSE-2.0
*
* Unless required by applicable law or agreed to in writing, software
* distributed under the License is distributed on an "AS IS" BASIS,
* WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
* See the License for the specific language governing permissions and
* limitations under the License.
*/

package com.fenlisproject.elf.core.net;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;

import org.apache.http.NameValuePair;
import org.apache.http.message.BasicNameValuePair;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.UnsupportedEncodingException;
import java.net.HttpURLConnection;
import java.net.ProtocolException;
import java.net.URL;
import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.List;

public class HttpRequest {

    private HttpURLConnection mConnection;

    private HttpRequest(String url) throws IOException {
        mConnection = (HttpURLConnection) new URL(url).openConnection();
    }

    public HttpURLConnection getHttpURLConnection() {
        return mConnection;
    }

    public void closeConnection() {
        try {
            mConnection.disconnect();
            mConnection = null;
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public InputStream getInputStream() {
        try {
            return mConnection.getInputStream();
        } catch (IOException e) {
            e.printStackTrace();
        } catch (Exception e) {
            e.printStackTrace();
        }
        return null;
    }

    public InputStreamReader getStreamReader() {
        InputStream is = getInputStream();
        return new InputStreamReader(is != null ? is : mConnection.getErrorStream());
    }

    public Bitmap getBitmapContent() {
        return BitmapFactory.decodeStream(getInputStream());
    }

    public String getTextContent() {
        try {
            BufferedReader r = new BufferedReader(getStreamReader());
            StringBuilder sb = new StringBuilder();
            String line;
            while ((line = r.readLine()) != null) {
                sb.append(line);
            }
            return sb.toString();
        } catch (IOException e) {
            e.printStackTrace();
        }
        return null;
    }

    public static class Builder {

        public static final int DEFAULT_CONNECTION_TIMEOUT = 8000;
        private int mConnectionTimeout;
        private boolean isUseCache;
        private String mRequestUrl;
        private String mRequestBody;
        private RequestMethod mRequestMethod;
        private List<NameValuePair> mUrlParams;
        private List<NameValuePair> mFormData;
        private List<NameValuePair> mHeaders;
        private boolean isForceUseCache;
        private int mRetryCount;

        public Builder(String url) {
            this.mRequestUrl = url;
            this.isUseCache = false;
            this.isForceUseCache = false;
            this.mRequestMethod = RequestMethod.GET;
            this.mUrlParams = new ArrayList<>();
            this.mFormData = new ArrayList<>();
            this.mHeaders = new ArrayList<>();
            this.mConnectionTimeout = DEFAULT_CONNECTION_TIMEOUT;
            this.mRetryCount = 1;
        }

        public Builder addRequestHeader(String key, String value) {
            this.mHeaders.add(new BasicNameValuePair(key, value));
            return this;
        }

        public Builder addUrlParams(String key, String value) {
            this.mUrlParams.add(new BasicNameValuePair(key, value));
            return this;
        }

        public Builder addFormData(String key, String value) {
            this.mFormData.add(new BasicNameValuePair(key, value));
            return this;
        }

        public Builder setUseCache(boolean useCache) {
            this.isUseCache = useCache;
            return this;
        }

        public Builder setURL(String url) {
            this.mRequestUrl = url;
            return this;
        }

        public Builder setRequestMethod(RequestMethod method) {
            this.mRequestMethod = method;
            return this;
        }

        public void setRequestBody(String mRequestBody) {
            this.mRequestBody = mRequestBody;
        }

        public Builder setForceCache(boolean forceUseCache) {
            this.isForceUseCache = forceUseCache;
            return this;
        }

        public Builder setConnectionTimeout(int connectionTimeout) {
            this.mConnectionTimeout = connectionTimeout;
            return this;
        }

        public void setRetryCount(int retryCount) {
            this.mRetryCount = retryCount;
        }

        private String generateQueryString() {
            StringBuilder result = new StringBuilder();
            boolean first = true;
            for (NameValuePair params : mUrlParams) {
                if (first) first = false;
                else result.append("&");
                try {
                    result.append(URLEncoder.encode(params.getName(), "UTF-8"));
                    result.append("=");
                    result.append(URLEncoder.encode(params.getValue(), "UTF-8"));
                } catch (UnsupportedEncodingException e) {
                    e.printStackTrace();
                }
            }
            return result.toString();
        }

        private String generateUrlEncodedFormData() {
            StringBuilder result = new StringBuilder();
            boolean first = true;
            for (NameValuePair params : mFormData) {
                if (first) first = false;
                else result.append("&");
                try {
                    result.append(URLEncoder.encode(params.getName(), "UTF-8"));
                    result.append("=");
                    result.append(URLEncoder.encode(params.getValue(), "UTF-8"));
                } catch (UnsupportedEncodingException e) {
                    e.printStackTrace();
                }
            }
            return result.toString();
        }

        private String getFullPathRequestUrl() {
            String queryString = generateQueryString();
            if (queryString.length() > 0) {
                return mRequestUrl + "?" + queryString;
            } else {
                return mRequestUrl;
            }
        }

        public HttpRequest create() {
            HttpRequest request = null;
            try {
                request = new HttpRequest(getFullPathRequestUrl());
            } catch (IOException e) {
                e.printStackTrace();
            }
            while (request != null && request.getHttpURLConnection() != null && mRetryCount-- > 0) {
                try {
                    request.getHttpURLConnection().setUseCaches(isUseCache);
                    request.getHttpURLConnection().setRequestMethod(mRequestMethod.name());
                    request.getHttpURLConnection().setConnectTimeout(mConnectionTimeout);
                    for (NameValuePair header : mHeaders) {
                        request.getHttpURLConnection()
                                .setRequestProperty(header.getName(), header.getValue());
                    }
                    if (mRequestMethod == RequestMethod.POST && mFormData.size() > 0) {
                        request.getHttpURLConnection()
                                .setRequestProperty("Content-Type", "application/x-www-form-urlencoded");
                        mRequestBody = generateUrlEncodedFormData();
                    }
                    if (mRequestBody != null) {
                        request.getHttpURLConnection().setDoInput(true);
                        request.getHttpURLConnection().setDoOutput(true);
                        OutputStream os = request.getHttpURLConnection().getOutputStream();
                        BufferedWriter writer = new BufferedWriter(new OutputStreamWriter(os, "UTF-8"));
                        writer.write(mRequestBody);
                        writer.flush();
                        writer.close();
                        os.close();
                    }
                    if (isForceUseCache) {
                        request.getHttpURLConnection()
                                .addRequestProperty("Cache-Control", "only-if-cached");
                    }
                    mRetryCount = 0;
                } catch (ProtocolException e) {
                    e.printStackTrace();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
            return request;
        }
    }
}
