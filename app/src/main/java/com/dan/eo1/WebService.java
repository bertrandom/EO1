package com.dan.eo1;

import okhttp3.ResponseBody;
import retrofit2.Call;
import retrofit2.http.GET;
import retrofit2.http.Query;
import retrofit2.http.Url;

public interface WebService {

    @GET("/")
    Call<ResponseBody> getPlaylists();

    @GET
    public Call<ResponseBody> getPlaylist(@Url String url);

}