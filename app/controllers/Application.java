package controllers;

import com.fasterxml.jackson.databind.node.ObjectNode;
import com.ning.http.client.AsyncHttpClient;
import com.ning.http.client.Response;
import com.ning.http.client.oauth.OAuthSignatureCalculator;
import com.ning.http.client.oauth.RequestToken;
import common.Utils;
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
import play.Logger;
import play.Play;
import play.libs.F;
import play.libs.Json;
import play.libs.oauth.OAuth;
import play.libs.oauth.OAuth.ConsumerKey;
import play.libs.oauth.OAuth.ServiceInfo;
import play.mvc.Controller;
import play.mvc.Result;
import views.html.index;

import java.net.URLDecoder;
import java.sql.Timestamp;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.Future;


public class Application extends Controller {

    Map<String, String> IndivoConfig = new HashMap<String, String>();

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

    /*
      * Define any extra CORS headers needed for option requests (see http://enable-cors.org/server.html for more info)
      */
    public static Result preflight(String all) {
        response().setHeader("Access-Control-Allow-Methods", "POST, GET, PUT, DELETE, OPTIONS");
        response().setHeader("Access-Control-Allow-Headers", "*, Content-Type, Accept");
        return ok();
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
