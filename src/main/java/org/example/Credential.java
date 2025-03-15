package org.example;

public class Credential {
    private int api_id;
    private String api_hash;
    private String phonenumber;

    public Credential(int api_id, String api_hash, String phonenumber) {
        this.api_id = api_id;
        this.api_hash = api_hash;
        this.phonenumber = phonenumber;
    }

    public int getApi_id() {
        return api_id;
    }

    public String getApi_hash() {
        return api_hash;
    }

    public String getPhonenumber() {
        return phonenumber;
    }
}
