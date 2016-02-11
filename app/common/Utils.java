package common;

import org.json.JSONException;
import org.json.JSONObject;
import org.json.XML;

import java.math.BigInteger;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

/**
 * Created by vijay on 2/11/2016.
 * This class will hold commonly used functions
 */
public class Utils {
    public static int PRETTY_PRINT_INDENT_FACTOR = 4;

    /**************************************************************************************
     * This function will create Md5 Hash from Java security class
     * Input :  string
     * Output : Md5 hash
     */

    public static String md5(String input) {

        String md5 = null;

        if(null == input) return null;

        try {

            //Create MessageDigest object for MD5
            MessageDigest digest = MessageDigest.getInstance("MD5");

            //Update input string in message digest
            digest.update(input.getBytes(), 0, input.length());

            //Converts message digest value in base 16 (hex)
            md5 = new BigInteger(1, digest.digest()).toString(16);

        } catch (NoSuchAlgorithmException e) {

            e.printStackTrace();
        }
        return md5;
    }

    /**************************************************************************************
     * This function will create json object from XML string
     * Input : XML string
     * Output : Json Object
     */

    public static String getJsonFromXML(String inputXML){
        String jsonPrettyPrintString="";
        try {

            JSONObject xmlJSONObj = XML.toJSONObject(inputXML);

             jsonPrettyPrintString = xmlJSONObj.toString(PRETTY_PRINT_INDENT_FACTOR);



        } catch (JSONException je) {

            System.out.println(je.toString());

        }

        return jsonPrettyPrintString;
    }


}
