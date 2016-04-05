package models;

import com.rethinkdb.net.*;

import java.awt.*;
import com.rethinkdb.net.Cursor;

import java.util.*;
import java.util.concurrent.TimeoutException;

import com.rethinkdb.RethinkDB;
import com.rethinkdb.net.Connection;
import com.rethinkdb.gen.ast.*;

public class User {

    private Connection conn;

    public static final RethinkDB r = RethinkDB.r;


    public User() throws TimeoutException {
        conn = r.connection().hostname("52.74.23.247").port(28015).connect();
        conn.use("peerhealth");
    }

    public String Signup(String username, String fullname, String email, String password){
        java.util.Map<String, Object> result1 = new HashMap<>();

        result1 = r.db("peerhealth").table("users").insert(r.array(
                r.hashMap("username", username).with("fullname",fullname).with("email",email).with("password",password).with("indivo_username",username).with("indivo_password",password))).run(conn);

        return result1.get("generated_keys").toString();
    }

    public Cursor Login(String username, String password){
        Cursor cursor = r.db("peerhealth").table("users").filter(row -> row.g("username").eq(username).and(row.g("password").eq(password))).map(val -> val.toJson()).run(conn);
        return cursor;
    }

    public Cursor GetUserProfile(String userid)  {

        Cursor cursor;
        cursor = r.table("usersprofile").filter(row-> row.g("userid").eq(userid)).outerJoin(r.db("peerhealth").table("users"), (users_profile_row, users_row) -> {
            return users_row.g("id").eq(users_profile_row.g("userid"));
        }).zip().map(val -> val.toJson()).run(conn);


        return cursor;

    }

    public Cursor GetUserHealthAnalysis(String userid) throws TimeoutException {

        Cursor cursor;
        cursor = r.db("peerhealth").table("health_analysis").filter(row-> row.g("userid").eq(userid)).map(val -> val.toJson()).run(conn);

        return cursor;

    }

    public Cursor InvitedList(String userid, int status){
        Cursor cursor;
        cursor = r.table("invite_users").filter(row -> row.g("userid").eq(userid).or(row.g("peerid").eq(userid)).and(row.g("status").eq(status))).map(val -> val.toJson()).run(conn);
        return cursor;
    }

    public Cursor GetMatchPeers(String userid){
        Cursor cursor;

        //query to fetch match peers which not there in friends list, ignored list and invited list
        cursor = r.db("peerhealth").table("users")
                .filter(row -> row.g("id").ne(userid))
                .filter((u2) -> {
                    return r.db("peerhealth").table("invite_users").getAll(userid).optArg("index", "userid").filter((invited_user) -> {
                        return invited_user.g("peerid").eq(u2.g("id"));
                    }).isEmpty();
                })
                .filter((u2) -> {
                    return r.db("peerhealth").table("invite_users").getAll(userid).optArg("index", "peerid").filter((invited_user) -> {
                        return invited_user.g("userid").eq(u2.g("id"));
                    }).isEmpty();
                })
                .pluck("fullname", "id").limit(1).map(val -> val.toJson()).run(conn);
        return cursor;
    }

}
