package controllers;

import com.fasterxml.jackson.databind.node.ObjectNode;
import com.ning.http.client.AsyncHttpClient;
import com.ning.http.client.Response;
import com.ning.http.client.oauth.OAuthSignatureCalculator;
import com.ning.http.client.oauth.RequestToken;
import com.rethinkdb.gen.ast.ReqlExpr;
import com.rethinkdb.gen.ast.ReqlFunction1;
import com.rethinkdb.gen.ast.ReqlFunction2;
import com.rethinkdb.net.Cursor;
import common.Utils;
import models.User;
import oauth.signpost.OAuthConsumer;
import oauth.signpost.commonshttp.CommonsHttpOAuthConsumer;
import oauth.signpost.signature.AuthorizationHeaderSigningStrategy;
import org.apache.http.HttpEntity;
import org.apache.http.HttpResponse;
import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.DefaultHttpClient;
import org.apache.http.util.EntityUtils;
import org.json.JSONArray;
import org.json.JSONObject;
import play.Logger;
import play.Play;
import play.libs.F;
import play.libs.Json;
import play.libs.oauth.OAuth;
import play.libs.oauth.OAuth.ConsumerKey;
import play.libs.oauth.OAuth.ServiceInfo;
import play.mvc.Controller;
import play.mvc.Result;
import scala.Int;
import views.html.index;

import java.net.URLDecoder;
import java.sql.Timestamp;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Future;
import java.util.concurrent.TimeoutException;
import java.net.ServerSocket;
import java.net.Socket;

import com.rethinkdb.RethinkDB;
import com.rethinkdb.net.Connection;
import com.rethinkdb.gen.ast.*;

import static jdk.nashorn.internal.objects.NativeFunction.function;


public class Application extends Controller {

    Map<String, String> IndivoConfig = new HashMap<String, String>();
    public static final RethinkDB r = RethinkDB.r;
    String account_id,full_name,username,password,record_id;

    static final String IndivoBaseUrl =  Play.application().configuration().getString("indivo.server.url");
    static final String IndivoConsumerKey = Play.application().configuration().getString("indivo.consumer.key");
    static final String IndivoConsumerSecret = Play.application().configuration().getString("indivo.consumer.key");
    static final String IndivoRequestTokenUrl = IndivoBaseUrl+"oauth/request_token";
    static final String IndivoAccessTokenUrl = IndivoBaseUrl+"oauth/access_token";
    static final String IndivoAuthUrl = IndivoBaseUrl+"oauth/authorize";
    static final String IndivoSessionCreateUrl = IndivoBaseUrl+"oauth/internal/session_create";


    static final ConsumerKey KEY = new ConsumerKey(IndivoConsumerKey,IndivoConsumerSecret);

    private static final ServiceInfo SERVICE_INFO = new ServiceInfo(IndivoRequestTokenUrl,IndivoAccessTokenUrl,IndivoAuthUrl,
            KEY);

    private static final OAuth Indivo = new OAuth(SERVICE_INFO);

    public Connection conn;

    public User user;

    public Application() throws TimeoutException {
        conn = r.connection().hostname("52.74.23.247").port(28015).connect();
        conn.use("peerhealth");
        user = new User();

    }

    /*
      * Define any extra CORS headers needed for option requests (see http://enable-cors.org/server.html for more info)

    public static Result preflight(String all) {
        response().setHeader("Access-Control-Allow-Methods", "POST, GET, PUT, DELETE, OPTIONS");
        response().setHeader("Access-Control-Allow-Headers", "*, Content-Type, Accept");
        return ok();
    }*/

    /**
     * This is sample function to connect to rethinkDB and insert and get data
     * @return
     * @throws TimeoutException
     */
    public Result checkDB() throws TimeoutException {


        //r.db("peerhealth").tableCreate("tv_shows").run(conn);
        r.table("tv_shows").insert(r.hashMap("hello", "world")).run(conn);
        //Cursor cursor = r.table("tv_shows").run(conn);
        Cursor cursor = r.table("tv_shows").map(val -> val.toJson()).run(conn);
        List users = cursor.toList();
        return ok(users.toString());
    }

    public Result PeerAccount() throws TimeoutException {

        final Map<String, String[]> values = request().body().asFormUrlEncoded();

        String username = values.get("username")[0];
        String password = values.get("password")[0];
        String email = values.get("email")[0];
        String fullname = values.get("fullname")[0];

        ObjectNode result = Json.newObject();

        Connection conn = r.connection().hostname("localhost").port(28015).connect();
        String userid;
        userid = user.Signup(username,fullname,email,password);

        return ok(userid);

    }

    public Result PeerLogin() throws TimeoutException {

        final Map<String, String[]> values = request().body().asFormUrlEncoded();

        String username = values.get("username")[0];
        String password = values.get("password")[0];

        ObjectNode result = Json.newObject();

        List users = user.Login(username,password).toList();
        return ok(users.toString());

    }

    public Result UserProfile(String userid) throws TimeoutException {

        List users = user.GetUserProfile(userid).toList();
        return ok(users.toString());

    }

    public Result GetUserDetailsByUsername() throws TimeoutException {
        final Map<String, String[]> values = request().body().asFormUrlEncoded();

        String username = values.get("username")[0];
        ObjectNode result = Json.newObject();

        Cursor cursor = r.db("peerhealth").table("users").filter(row -> row.g("username").eq(username)).map(val -> val.toJson()).run(conn);
        List users = cursor.toList();
        return ok(users.toString());

    }

    public Result GetUserDetailsByEmail() throws TimeoutException {
        final Map<String, String[]> values = request().body().asFormUrlEncoded();

        String username = values.get("username")[0];
        ObjectNode result = Json.newObject();

        Connection conn = r.connection().hostname("localhost").port(28015).connect();
        Cursor cursor = r.db("peerhealth").table("users").filter(row -> row.g("email").eq(username)).map(val -> val.toJson()).run(conn);

        List users = cursor.toList();
        return ok(users.toString());

    }


    public Result Invite() throws TimeoutException {

        final Map<String, String[]> values = request().body().asFormUrlEncoded();

        String userid = values.get("userid")[0];
        String peerid = values.get("peerid")[0];
        String message = values.get("message")[0];
        String status = values.get("status")[0];

        ObjectNode result = Json.newObject();


        r.table("invite_users").insert(r.array(
                r.hashMap("userid",userid).with("peerid",peerid).with("message",message).with("status",status))).run(conn);

        return ok(result.toString());

    }


    public Result SendAccept() throws TimeoutException {

        final Map<String, String[]> values = request().body().asFormUrlEncoded();

        String userid = values.get("userid")[0];
        String peerid = values.get("peerid")[0];
        String status = values.get("status")[0];

        System.out.println(userid+""+peerid+""+status);
        ObjectNode result = Json.newObject();


        r.table("invite_users").filter(row -> row.g("userid").eq(userid).and(row.g("peerid").eq(peerid)).or(row.g("userid").eq(peerid).and(row.g("peerid").eq(userid)))).update(r.hashMap("status",status)).run(conn);


        return ok(result.toString());

    }

    public Result Ignore() throws TimeoutException {

        final Map<String, String[]> values = request().body().asFormUrlEncoded();

        String userid = values.get("userid")[0];
        String peerid = values.get("peerid")[0];
        String message = values.get("message")[0];

        ObjectNode result = Json.newObject();

        r.table("ignore_users").insert(r.array(
                r.hashMap("userid",userid).with("peerid",peerid).with("message",message).with("status",0))).run(conn);

        return ok(result.toString());

    }

    public Result InvitedList(String userid) throws TimeoutException  {

        List users = user.InvitedList(userid,0).toList();

        //System.out.println(users.toString());

        JSONArray jsonArray =new JSONArray(users.toString());
        //System.out.println(userid);
        //creating empty json array
        ArrayList<JSONObject> userlist= new ArrayList<JSONObject>();

        //looping the peers list to get a profile of the peer
        for (int i=0;i<jsonArray.length();i++)
        {

            JSONObject jsonObject =  jsonArray.getJSONObject(i);

            //calling a function to get profile of the peer
            /*
            * Need to change the way of taking profile details by calling a function
            * */

            String profileid = null;
            int editable;
            if(!jsonObject.getString("userid").equals(userid)) {
                profileid = jsonObject.getString("userid");
                editable = 1;
            }else{
                profileid = jsonObject.getString("peerid");
                editable = 0;
            }

            JSONArray jsonArray1=new JSONArray(user.GetUserProfile(profileid).toList().toString());


            //assigning the profile picture url to the variable
            String  profilepicUrl =jsonArray1.getJSONObject(0).getString("profilepicture");
            String  fullname =jsonArray1.getJSONObject(0).getString("fullname");

            JSONObject jsonObject1  = new JSONObject();

            //adding values to an array
            jsonObject1.put("profilepicture",profilepicUrl);

            jsonObject1.put("userid",profileid);

            jsonObject1.put("fullname",fullname);

            jsonObject1.put("editable",editable);

            userlist.add(jsonObject1);

        }

        return ok(userlist.toString());

    }


    public Result FriendsList(String userid) throws TimeoutException {

        List users = user.InvitedList(userid,1).toList();

        JSONArray jsonArray =new JSONArray(users.toString());

        //creating empty json array
        ArrayList<JSONObject> userlist= new ArrayList<JSONObject>();

        //looping the peers list to get a profile of the peer
        for (int i=0;i<jsonArray.length();i++)
        {

            JSONObject jsonObject =  jsonArray.getJSONObject(i);

            //calling a function to get profile of the peer
            /*
            * Need to change the way of taking profile details by calling a function
            * */

            String profileid = null;
            int editable;
            if(!jsonObject.getString("userid").equals(userid)) {
                profileid = jsonObject.getString("userid");
                editable = 1;
            }else{
                profileid = jsonObject.getString("peerid");
                editable = 0;
            }

            JSONArray jsonArray1=new JSONArray(user.GetUserProfile(profileid).toList().toString());


            //assigning the profile picture url to the variable
            String  profilepicUrl =jsonArray1.getJSONObject(0).getString("profilepicture");
            String  fullname =jsonArray1.getJSONObject(0).getString("fullname");

            JSONObject jsonObject1  = new JSONObject();

            //adding values to an array
            jsonObject1.put("profilepicture",profilepicUrl);

            jsonObject1.put("userid",profileid);

            jsonObject1.put("fullname",fullname);

            jsonObject1.put("editable",editable);

            userlist.add(jsonObject1);

        }



        return ok(userlist.toString());

    }

    public Result AddConversation() throws TimeoutException {

        final Map<String, String[]> values = request().body().asFormUrlEncoded();

        String from_id = values.get("from_id")[0];
        String to_id = values.get("to_id")[0];
        String message = values.get("message")[0];

        ObjectNode result = Json.newObject();

        Connection conn = r.connection().hostname("52.74.23.247").port(28015).connect();

        Map<String, Object> result1 = new HashMap<>();

        SimpleDateFormat current_time =
                new SimpleDateFormat ("yyyy-MM-dd");

        Date currentDate = new Date("yyyy-MM-dd");

        System.out.println(currentDate.toString());

        result1 = r.db("peerhealth").table("conversation").insert(r.hashMap("from_id", from_id)
                .with("to_id", to_id)
                .with("created",current_time)
                .with("modified",current_time)
        ).run(conn);

        String con_id = result1.get("generated_keys").toString();

        r.db("peerhealth").table("conversation_reply").insert(r.hashMap("from_id", from_id)
                .with("to_id", to_id)
                .with("con_id",con_id)
                .with("timestamp",System.currentTimeMillis())
                .with("created",current_time)
                .with("modified",current_time)
        ).run(conn);



        return ok(result.toString());
    }

    /*
    * Function to get the match peers based on similarities and health score and which not there in friends list, ignored list and invited list
    * userid : Logged in userid to get the peers.
    * */
    public Result GetMatchPeers(String userid) throws TimeoutException {

        ObjectNode result = Json.newObject();

        List users = user.GetMatchPeers(userid).toList();

        JSONArray jsonArray =new JSONArray(users.toString());

        //creating empty json array
        ArrayList<JSONObject> userlist= new ArrayList<JSONObject>();

        //looping the peers list to get a profile of the peer
        for (int i=0;i<jsonArray.length();i++)
        {

            JSONObject jsonObject =  jsonArray.getJSONObject(i);

            //calling a function to get profile of the peer
            /*
            * Need to change the way of taking profile details by calling a function
            * */

            JSONArray jsonArray1=new JSONArray(user.GetUserProfile(jsonObject.getString("id")).toList().toString());

            JSONArray jsonArray2=new JSONArray(user.GetUserHealthAnalysis(jsonObject.getString("id")).toList().toString());

            //assigning the profile picture url to the variable
            String  profilepicUrl =jsonArray1.getJSONObject(0).getString("profilepicture");

            String  health_score =jsonArray2.getJSONObject(0).getString("health_score");

            String  similarities =jsonArray2.getJSONObject(0).getString("similarities");

            JSONObject jsonObject1  = new JSONObject();

            //adding values to an array
            jsonObject1.put("profilepicture",profilepicUrl);

            jsonObject1.put("userid",jsonObject.getString("id"));

            jsonObject1.put("fullname",jsonObject.getString("fullname"));

            jsonObject1.put("health_score",health_score);

            jsonObject1.put("similarities",similarities);

            userlist.add(jsonObject1);

        }

        return ok(userlist.toString());

    }


    public Result index() {
        return ok(index.render("Hello RESTful Exercise!"));
    }

    /**************************************************************************************
     * This function will take Indivo login credetails and validate with Indivo API and get
     * session token and secret for further calls to sign and get / post data to Indivo
     * here we will have our own session id which will used to validate requests between PeerHealth
     * apps and API
     * Input : username and password(plain text as of now have to figure out from Indivo to make it as MD5)
     * Output : Json object with session id and raw output from Indivo
     */
    public Result Login() {

        final Map<String, String[]> values = request().body().asFormUrlEncoded();

        String username = values.get("username")[0];
        String password = values.get("password")[0];

        ObjectNode result = Json.newObject();

        OAuthSignatureCalculator calc = null;

        com.ning.http.client.oauth.ConsumerKey consumerAuth1 = new com.ning.http.client.oauth.ConsumerKey(
                IndivoConsumerKey,IndivoConsumerSecret);
        com.ning.http.client.oauth.RequestToken userAuth1 = new com.ning.http.client.oauth.RequestToken(
                "", ""); // Here we dont have to send empty initially

        calc = new OAuthSignatureCalculator(consumerAuth1, userAuth1);
        AsyncHttpClient client = new AsyncHttpClient();
        client.setSignatureCalculator(calc);

        try {
            Future<Response> f = client.preparePost(IndivoSessionCreateUrl)
                    .addFormParam("password" , password)
                    .addFormParam("username" , username)
                    .addHeader("Content-Type","application/x-www-form-urlencoded").execute();
            String responseBody = f.get().getResponseBody();


            if(responseBody.contains("oauth_token_secret")){
                // response from indivo server example oauth_token_secret=0ivN9IWsElJotFQkrsag&oauth_token=6OoH6d9WR8Y1sUuh6Lxv&account_id=johnsmith%40indivo.org
                // responseBody = StringEscapeUtils.unescapeHtml4(responseBody);
                responseBody= URLDecoder.decode(responseBody, "UTF-8");
                Logger.debug("responseBody" +responseBody);

                String[] parts = responseBody.split("&");

                for (String part : parts) {
                    //do something interesting here
                    String[] nameAndValue = part.split("=");
                    IndivoConfig.put(nameAndValue[0],nameAndValue[1]);
                }
                session("token", IndivoConfig.get("oauth_token"));
                session("secret", IndivoConfig.get("oauth_token_secret"));
                session("sessionid",Utils.md5(IndivoConfig.get("oauth_token")+IndivoConfig.get("oauth_token_secret")));
                Logger.debug("IndivoConfig" +IndivoConfig);


                result.put("result","true" );
                result.put("sessionid",Utils.md5(IndivoConfig.get("oauth_token")+IndivoConfig.get("oauth_token_secret")));
                result.put("rawout",responseBody);

            }else{
                result.put("result","true" );
                result.put("sessionid","false" );
                result.put("rawout",responseBody );

            }

        } catch (Exception e) {
            Logger.debug("Exception while getting people" + e, e);
        }

        return ok(result);

    }


    public String internalLogin(String uname,String pass){

        String responseBody="";

        ObjectNode result = Json.newObject();

        OAuthSignatureCalculator calc = null;

        com.ning.http.client.oauth.ConsumerKey consumerAuth1 = new com.ning.http.client.oauth.ConsumerKey(
                IndivoConsumerKey,IndivoConsumerSecret);
        com.ning.http.client.oauth.RequestToken userAuth1 = new com.ning.http.client.oauth.RequestToken(
                "", ""); // Here we dont have to send empty initially

        calc = new OAuthSignatureCalculator(consumerAuth1, userAuth1);
        AsyncHttpClient client = new AsyncHttpClient();
        client.setSignatureCalculator(calc);

        try {
            Future<Response> f = client.preparePost(IndivoSessionCreateUrl)
                    .addFormParam("password" , pass)
                    .addFormParam("username" , uname)
                    .addHeader("Content-Type","application/x-www-form-urlencoded").execute();
            responseBody = f.get().getResponseBody();


            if(responseBody.contains("oauth_token_secret")) {
                // response from indivo server example oauth_token_secret=0ivN9IWsElJotFQkrsag&oauth_token=6OoH6d9WR8Y1sUuh6Lxv&account_id=johnsmith%40indivo.org
                // responseBody = StringEscapeUtils.unescapeHtml4(responseBody);
                responseBody = URLDecoder.decode(responseBody, "UTF-8");
                Logger.debug("responseBody" + responseBody);

            }

        } catch (Exception e) {
            Logger.debug("Exception while getting people" + e, e);
        }

        return responseBody;
    }
    /**************************************************************************************
     * This function will create Indivo account
     * Input : username and password(plain text as of now have to figure out from Indivo to make it as MD5)
     * Output : Json object with session id and raw output from Indivo
     * /accounts/{ACCOUNT_EMAIL}/set-state  - state [active]

     'accounts/' + accEmail + '/' + 'authsystems/'  -- username, password , system

     urlBase + 'accounts/'  -- account_id,contact_email,full_name,primary_secret_p,secondary_secret_p
     */
    public String IndivoAccountCreate(){


        String responseBody="";
        OAuthSignatureCalculator calc = null;

        com.ning.http.client.oauth.ConsumerKey consumerAuth1 = new com.ning.http.client.oauth.ConsumerKey(
                IndivoConsumerKey,IndivoConsumerSecret);
        com.ning.http.client.oauth.RequestToken userAuth1 = new com.ning.http.client.oauth.RequestToken(
                "", ""); // Here we dont have to send empty initially

        calc = new OAuthSignatureCalculator(consumerAuth1, userAuth1);
        AsyncHttpClient client = new AsyncHttpClient();
        client.setSignatureCalculator(calc);
        try{

            Future<Response> f = client.preparePost(IndivoBaseUrl+"accounts/")
                    .addFormParam("account_id" , account_id)
                    .addFormParam("contact_email" , account_id)
                    .addFormParam("full_name" , full_name)
                    .addFormParam("primary_secret_p" , "0")
                    .addFormParam("secondary_secret_p" , "0")
                    .addHeader("Content-Type","application/x-www-form-urlencoded").execute();
            responseBody = f.get().getResponseBody();

        }catch (Exception e){
            //// TODO: 2/15/2016
        }
        Logger.debug("accounts==>" +responseBody);
        return responseBody;
    }

    public String IndivoRecordOwner(){

        String ownerBody="";
        OAuthSignatureCalculator calc = null;

        com.ning.http.client.oauth.ConsumerKey consumerAuth1 = new com.ning.http.client.oauth.ConsumerKey(
                IndivoConsumerKey,IndivoConsumerSecret);
        com.ning.http.client.oauth.RequestToken userAuth1 = new com.ning.http.client.oauth.RequestToken(
                "", ""); // Here we dont have to send empty initially

        calc = new OAuthSignatureCalculator(consumerAuth1, userAuth1);
        AsyncHttpClient client = new AsyncHttpClient();
        client.setSignatureCalculator(calc);
        try{

            Future<Response> fauth = client.prepareGet("http://indivo.smartrx.in:8001/records/462d94ce-8e62-4c2c-88e9-539959c38458/")
                    .addHeader("Content-Type","application/x-www-form-urlencoded").execute();
            ownerBody = fauth.get().getResponseBody();
            Logger.debug("owner of the record" +ownerBody);
            //// TODO: 2/15/2016

        }catch (Exception e){
            //// TODO: 2/15/2016
        }
        return ownerBody;
    }

    public String IndivoAuthSet(){
        String pbody="";

        OAuthSignatureCalculator calc = null;

        com.ning.http.client.oauth.ConsumerKey consumerAuth1 = new com.ning.http.client.oauth.ConsumerKey(
                IndivoConsumerKey,IndivoConsumerSecret);
        com.ning.http.client.oauth.RequestToken userAuth1 = new com.ning.http.client.oauth.RequestToken(
                "", ""); // Here we dont have to send empty initially

        calc = new OAuthSignatureCalculator(consumerAuth1, userAuth1);
        AsyncHttpClient client = new AsyncHttpClient();
        client.setSignatureCalculator(calc);
        try{

            Future<Response> fauth = client.preparePost(IndivoBaseUrl+"accounts/"+account_id+"/authsystems/")
                    .addFormParam("username" ,username)
                    .addFormParam("password" ,password)
                    .addFormParam("system" , "password")
                    .addHeader("Content-Type","application/x-www-form-urlencoded").execute();
            pbody = fauth.get().getResponseBody();
            Logger.debug("accounts auth==>" +pbody);

        }catch (Exception e){

        }

        return pbody;
    }

    public String IndivoAccountSetState(){
        String stbody="";

        OAuthSignatureCalculator calc = null;

        com.ning.http.client.oauth.ConsumerKey consumerAuth1 = new com.ning.http.client.oauth.ConsumerKey(
                IndivoConsumerKey,IndivoConsumerSecret);
        com.ning.http.client.oauth.RequestToken userAuth1 = new com.ning.http.client.oauth.RequestToken(
                "",""); // Here we dont have to send empty initially

        calc = new OAuthSignatureCalculator(consumerAuth1, userAuth1);
        AsyncHttpClient client = new AsyncHttpClient();
        client.setSignatureCalculator(calc);
        try{

            Future<Response> fauth = client.preparePost(IndivoBaseUrl+"accounts/"+account_id+"/set-state")
                    .addFormParam("state" , "active")
                    .addHeader("Content-Type","application/x-www-form-urlencoded").execute();
            stbody = fauth.get().getResponseBody();
            Logger.debug("set state" +stbody);
            //// TODO: 2/15/2016

        }catch (Exception e){
            //// TODO: 2/15/2016
        }
        return stbody;
    }

    public String IndivoCreateDemographic(){

        try{
            // here we have to add demographic records otherwise he wont show up in indivo ui
            /**
             * <Demographics xmlns="http://indivo.org/vocab/xml/documents#">
             <dateOfBirth>1939-11-15</dateOfBirth>
             <gender>male</gender>
             <email>test@fake.org</email>
             <ethnicity>Scottish</ethnicity>
             <preferredLanguage>EN</preferredLanguage>
             <race>caucasian</race>
             <Name>
             <familyName>Wayne</familyName>
             <givenName>Bruce</givenName>
             <middleName>Quentin</middleName>
             <prefix>Mr</prefix>
             <suffix>Jr</suffix>
             </Name>
             <Telephone>
             <type>h</type>
             <number>555-5555</number>
             <preferred>true</preferred>
             </Telephone>
             <Telephone>
             <type>c</type>
             <number>555-6666</number>
             </Telephone>
             <Address>
             <country>USA</country>
             <city>Gotham</city>
             <postalCode>90210</postalCode>
             <region>secret</region>
             <street>1007 Mountain Drive</street>
             </Address>
             </Demographics>
             *
             */
            OAuthConsumer consumer = new CommonsHttpOAuthConsumer(IndivoConsumerKey,IndivoConsumerSecret);
            consumer.setTokenWithSecret("","");

            HttpPost request = new HttpPost(IndivoBaseUrl+ "records/");
            Logger.debug("IndivoBaseUrl" +IndivoBaseUrl+ "records/");
            String recordXML="<?xml version=\"1.0\" encoding=\"utf-8\" ?>\n" +
                    "<Demographics xmlns=\"http://indivo.org/vocab/xml/documents#\">\n" +
                    "    <dateOfBirth>1939-11-15</dateOfBirth>\n" +
                    "    <gender>male</gender>\n" +
                    "    <email>"+account_id+"</email>\n" +
                    "    <ethnicity></ethnicity>\n" +
                    "    <preferredLanguage></preferredLanguage>\n" +
                    "    <race></race>\n" +
                    "    <Name>\n" +
                    "        <familyName>"+full_name+"</familyName>\n" +
                    "        <givenName></givenName>\n" +
                    "        <middleName></middleName>\n" +
                    "        <prefix></prefix>\n" +
                    "        <suffix></suffix>\n" +
                    "    </Name>\n" +
                    "\n" +
                    "    <Address>\n" +
                    "        <country></country>\n" +
                    "        <city></city>\n" +
                    "        <postalCode></postalCode>\n" +
                    "        <region></region>\n" +
                    "        <street></street>\n" +
                    "    </Address>\n" +
                    "</Demographics>";

            Logger.debug("recordXML==>" +recordXML);

            StringEntity body = new StringEntity(recordXML);
            body.setContentType("application/xml");
            request.setEntity(body);

            consumer.setSigningStrategy(new AuthorizationHeaderSigningStrategy());
            consumer.sign(request);

            // send the request
            HttpClient httpClient = new DefaultHttpClient();

            HttpResponse response = httpClient.execute(request);

            HttpEntity entity = response.getEntity();
            String responseString = EntityUtils.toString(entity, "UTF-8");
            //return ok(Utils.getJsonObjectFromXML(responseString)+"");
            JSONObject userRecord= Utils.getJsonObjectFromXML(responseString);
            Logger.debug("userRecord==>" +userRecord);
            JSONObject ur = userRecord.getJSONObject("Record");
            record_id = ur.getString("id");

            Logger.debug("record_id==>" +record_id);


        }catch (Exception e){
            //// TODO: 2/15/2016
        }

        return record_id;
    }

    public String IndivoSetRecordOwner(){

        String rownbody="";



        ObjectNode result = Json.newObject();




        try{

            OAuthConsumer consumerSet = new CommonsHttpOAuthConsumer(IndivoConsumerKey,
                    IndivoConsumerSecret);
            consumerSet.setTokenWithSecret("","");

            HttpPost request = new HttpPost(IndivoBaseUrl+"records/"+record_id+"/owner");

            StringEntity body = new StringEntity(account_id, "UTF8");
            body.setContentType("application/xml");
            request.setEntity(body);

            consumerSet.setSigningStrategy(new AuthorizationHeaderSigningStrategy());
            consumerSet.sign(request);


            // send the request
            HttpClient httpClient = new DefaultHttpClient();

            HttpResponse response = httpClient.execute(request);
            Logger.debug("Status line", "" + response.getStatusLine().getStatusCode());
            HttpEntity entity = response.getEntity();
            rownbody = EntityUtils.toString(entity, "UTF-8");

            Logger.debug("set owner" +rownbody);
            //// TODO: 2/15/2016

        }catch (Exception e){
            //// TODO: 2/15/2016
        }

        return rownbody;
    }
    public Result CreateAccount() {

        final Map<String, String[]> values = request().body().asFormUrlEncoded();

        String setState="",setOwner="";

        this.account_id = values.get("account_id")[0];
        this.full_name = values.get("full_name")[0];
        this.username = values.get("username")[0];
        this.password = values.get("password")[0];



        ObjectNode result = Json.newObject();


        String AccountInfo=IndivoAccountCreate();



        if(AccountInfo.contains("Account")) {

            String setAuth = IndivoAuthSet();

            if (setAuth.contains("<ok/>")) {

                //setState = IndivoAccountSetState();

                //if(setState.contains("<ok/>")){
/*                    String _iLogin=internalLogin(username,password);

                    String[] parts = _iLogin.split("&");

                    Map<String, String> __IndivoConfig = new HashMap<String, String>();

                    for (String part : parts) {
                        //do something interesting here
                        String[] nameAndValue = part.split("=");
                        __IndivoConfig.put(nameAndValue[0],nameAndValue[1]);
                    }*/

                IndivoCreateDemographic();

                if(record_id.length()>0){

                    setOwner = IndivoSetRecordOwner();



                }

                //}
            }

            result.put("result","true" );
            result.put("account_added","true" );
            result.put("setAuth",setAuth );
            result.put("record_id",record_id);
            result.put("setOwner",setOwner);
        }else{
            result.put("result","true" );
            result.put("account_added","false" );


        }



        return ok(result);

    }
    /**************************************************************************************
     * This function will get list of records for the logged in Indivo User
     * Input : recordid, sessionid
     * Output : Json object from raw output from Indivo
     */
    public Result Records() {

        final Map<String, String[]> values = request().body().asFormUrlEncoded();

        String sessionid = values.get("sessionid")[0];

        ObjectNode result = Json.newObject();

        OAuthSignatureCalculator calc = null;

        com.ning.http.client.oauth.ConsumerKey consumerAuth1 = new com.ning.http.client.oauth.ConsumerKey(
                IndivoConsumerKey,IndivoConsumerSecret);
        com.ning.http.client.oauth.RequestToken userAuth1 = new com.ning.http.client.oauth.RequestToken(
                IndivoConfig.get("oauth_token"),IndivoConfig.get("oauth_token_secret"));

        calc = new OAuthSignatureCalculator(consumerAuth1, userAuth1);
        AsyncHttpClient client = new AsyncHttpClient();
        client.setSignatureCalculator(calc);

        try {
            Future<Response> f = client.prepareGet(IndivoBaseUrl+ "accounts/" + IndivoConfig.get("account_id") + "/records/")
                    .addHeader("Content-Type","application/x-www-form-urlencoded").execute();
            String responseBody = f.get().getResponseBody();
            if(responseBody.contains("Records")){
                return ok(Utils.getJsonFromXML(responseBody));
            }else{
                return ok(responseBody);
            }
            /*Sample response
            <?xml version="1.0" encoding="utf-8" ?>
            <Records>
            <Record id="6663da59-187a-4da3-8654-7688c32098f0" label="Mary" />
            <Record id="7cba5c02-741c-4fe3-97a3-ac7627a15027" label="Vik Anantha " />
            <Record id="973d344a-c70d-44df-96f0-4e8b3d7cbf41" label="John S. Smith" />
            </Records>
             */


        } catch (Exception e) {
            Logger.debug("Exception while getting people" + e, e);
        }

        return ok(result);
    }
    /**************************************************************************************
     * This function will get list of Allergies for the logged in Indivo User with the recordid
     * Input : recordid, sessionid
     * Output : Raw output from Indivo
     */
    public Result Allergies() {

        final Map<String, String[]> values = request().body().asFormUrlEncoded();

        String sessionid = values.get("sessionid")[0];
        String recordid = values.get("recordid")[0];

        ObjectNode result = Json.newObject();

        OAuthSignatureCalculator calc = null;

        com.ning.http.client.oauth.ConsumerKey consumerAuth1 = new com.ning.http.client.oauth.ConsumerKey(
                IndivoConsumerKey,IndivoConsumerSecret);
        com.ning.http.client.oauth.RequestToken userAuth1 = new com.ning.http.client.oauth.RequestToken(
                IndivoConfig.get("oauth_token"),IndivoConfig.get("oauth_token_secret"));

        calc = new OAuthSignatureCalculator(consumerAuth1, userAuth1);
        AsyncHttpClient client = new AsyncHttpClient();
        client.setSignatureCalculator(calc);

        try {
            Future<Response> f = client.prepareGet(IndivoBaseUrl+ "records/" + recordid + "/allergies/")
                    .addHeader("Content-Type","application/x-www-form-urlencoded").execute();
            String responseBody = f.get().getResponseBody();
            return ok(responseBody);

        } catch (Exception e) {
            Logger.debug("Exception while getting people" + e, e);
        }

        return ok(result);
    }


    /**************************************************************************************
     * This function will get list of Documents for the logged in Indivo User with the recordid
     * Input : recordid, sessionid
     * Output : Raw output from Indivo
     */
    public Result Documents() {

        final Map<String, String[]> values = request().body().asFormUrlEncoded();

        String sessionid = values.get("sessionid")[0];
        String recordid = values.get("recordid")[0];

        ObjectNode result = Json.newObject();

        OAuthSignatureCalculator calc = null;

        com.ning.http.client.oauth.ConsumerKey consumerAuth1 = new com.ning.http.client.oauth.ConsumerKey(
                IndivoConsumerKey,IndivoConsumerSecret);
        com.ning.http.client.oauth.RequestToken userAuth1 = new com.ning.http.client.oauth.RequestToken(
                IndivoConfig.get("oauth_token"),IndivoConfig.get("oauth_token_secret"));

        calc = new OAuthSignatureCalculator(consumerAuth1, userAuth1);
        AsyncHttpClient client = new AsyncHttpClient();
        client.setSignatureCalculator(calc);

        try {
            Future<Response> f = client.prepareGet(IndivoBaseUrl+ "records/" + recordid + "/documents/")
                    .addHeader("Content-Type","application/x-www-form-urlencoded").execute();
            String responseBody = f.get().getResponseBody();
            if(responseBody.contains("Documents")){
                return ok(Utils.getJsonFromXML(responseBody));
            }else{
                return ok(responseBody);
            }

        } catch (Exception e) {
            Logger.debug("Exception while getting people" + e, e);
        }

        return ok(result);
    }


    /**************************************************************************************
     * This function will get document details the logged in Indivo User with the recordid
     * Input : recordid,documentid, sessionid
     * Output : Raw output from Indivo
     */
    public Result getDocDetails() {

        final Map<String, String[]> values = request().body().asFormUrlEncoded();

        String sessionid = values.get("sessionid")[0];
        String recordid = values.get("recordid")[0];
        String docid = values.get("docid")[0];

        ObjectNode result = Json.newObject();

        OAuthSignatureCalculator calc = null;

        com.ning.http.client.oauth.ConsumerKey consumerAuth1 = new com.ning.http.client.oauth.ConsumerKey(
                IndivoConsumerKey,IndivoConsumerSecret);
        com.ning.http.client.oauth.RequestToken userAuth1 = new com.ning.http.client.oauth.RequestToken(
                IndivoConfig.get("oauth_token"),IndivoConfig.get("oauth_token_secret"));

        calc = new OAuthSignatureCalculator(consumerAuth1, userAuth1);
        AsyncHttpClient client = new AsyncHttpClient();
        client.setSignatureCalculator(calc);

        try {
            Future<Response> f = client.prepareGet(IndivoBaseUrl+ "records/" + recordid + "/documents/"+docid)
                    .addHeader("Content-Type","application/x-www-form-urlencoded").execute();
            String responseBody = f.get().getResponseBody();
            //if(responseBody.contains("Documents")){
            return ok(Utils.getJsonFromXML(responseBody));
            //}else{
            //   return ok(responseBody);
            //}

        } catch (Exception e) {
            Logger.debug("Exception while getting people" + e, e);
        }

        return ok(result);
    }


    /**************************************************************************************
     * This function save vitals logged in Indivo User with the recordid
     * Input : recordid,documentid, sessionid
     * Output : Raw output from Indivo
     */
    public Result saveVitals() {

        final Map<String, String[]> values = request().body().asFormUrlEncoded();

        String sessionid = values.get("sessionid")[0];
        String recordid = values.get("recordid")[0];

        String vname = values.get("vname")[0];
        String vvalue = values.get("vvalue")[0];
        String vunit = values.get("vunit")[0];

        java.util.Date date= new java.util.Date();
        Timestamp now = new Timestamp(date.getTime());

        String vitalxml = "<VitalSign xmlns=\"http://indivo.org/vocab/xml/documents#\"><name>"+vname+"</name>\n" +
                " <measuredBy>"+IndivoConfig.get("account_id")+"</measuredBy>\n<dateMeasuredStart>"
                + now + "</dateMeasuredStart>\n<dateMeasuredEnd>"
                + now + "</dateMeasuredEnd>\n<result><value>"
                + vvalue + "</value><unit>"+vunit+"</unit></result>\n</VitalSign>";

        ObjectNode result = Json.newObject();

        OAuthConsumer consumer = new CommonsHttpOAuthConsumer(IndivoConsumerKey,
                IndivoConsumerSecret);
        consumer.setTokenWithSecret(IndivoConfig.get("oauth_token"), IndivoConfig.get("oauth_token_secret"));

        try {
            HttpPost request = new HttpPost(IndivoBaseUrl+ "records/" + recordid + "/documents/");

            StringEntity body = new StringEntity(vitalxml);
            body.setContentType("application/xml");
            request.setEntity(body);

            consumer.setSigningStrategy(new AuthorizationHeaderSigningStrategy());
            consumer.sign(request);


            // send the request
            HttpClient httpClient = new DefaultHttpClient();

            HttpResponse response = httpClient.execute(request);

            HttpEntity entity = response.getEntity();
            String responseString = EntityUtils.toString(entity, "UTF-8");

            //if(responseBody.contains("Documents")){
            return ok(Utils.getJsonFromXML(responseString));
            //}else{
            //   return ok(responseBody);
            //}

        } catch (Exception e) {
            Logger.debug("Exception while getting people" + e, e);
        }

        return ok(result);
    }

    /**************************************************************************************
     * This function will create the Request Token
     * Input : None
     * Output : Request Token
     */
    private static F.Option<RequestToken> getSessionTokenPair() {
        if (session().containsKey("token")) {
            return F.Option.Some(new RequestToken(session("token"), session("secret")));
        }
        return F.Option.None();
    }


}
