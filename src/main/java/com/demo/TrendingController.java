package com.demo;

import java.io.BufferedOutputStream;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.math.BigDecimal;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.Iterator;
import java.util.List;
import java.util.TimeZone;
import java.util.UUID;

import org.apache.commons.io.FilenameUtils;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.joda.time.DateTime;
import org.joda.time.DateTimeZone;
import org.joda.time.format.DateTimeFormatter;
import org.joda.time.format.ISODateTimeFormat;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.util.FileCopyUtils;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import com.amazonaws.auth.AWSCredentials;
import com.amazonaws.auth.BasicAWSCredentials;
import com.amazonaws.regions.Region;
import com.amazonaws.regions.Regions;
import com.amazonaws.services.dynamodbv2.AmazonDynamoDBClient;
import com.amazonaws.services.dynamodbv2.document.DynamoDB;
import com.amazonaws.services.dynamodbv2.document.Item;
import com.amazonaws.services.dynamodbv2.document.ItemCollection;
import com.amazonaws.services.dynamodbv2.document.QueryOutcome;
import com.amazonaws.services.dynamodbv2.document.Table;
import com.amazonaws.services.dynamodbv2.document.spec.QuerySpec;
import com.amazonaws.services.dynamodbv2.document.utils.ValueMap;
import com.amazonaws.services.s3.AmazonS3;
import com.amazonaws.services.s3.AmazonS3Client;
import com.amazonaws.services.s3.model.AccessControlList;
import com.amazonaws.services.s3.model.CannedAccessControlList;
import com.amazonaws.services.s3.model.PutObjectRequest;
import com.amazonaws.services.s3.model.PutObjectResult;

@Controller
public class TrendingController {
	private Log log = LogFactory.getLog(TrendingController.class);
	private static String KEYID="<YOUR KEYID>";
	private static String SECRET_ACCESS_KEY="<YOUR ACCESS KEY>";
	private static String BUCKETNAME="rudygunawanbucket";//S3
	private static String TABLENAME="Post"; //DynamoDB
    private static AWSCredentials credentials = new BasicAWSCredentials(KEYID, SECRET_ACCESS_KEY);
    
    @RequestMapping(value="/upload", method=RequestMethod.GET)
    public String uploadGet(Model model)
    {
    	model.addAttribute("post",new PostBean());
    	return "upload";
    }
    
    
    
    @RequestMapping(value="/", method=RequestMethod.GET)
    public String index(Model model) {
    	
    	AmazonDynamoDBClient dbClient = new AmazonDynamoDBClient(credentials);
    	dbClient.setRegion(Region.getRegion(Regions.AP_SOUTHEAST_1));
    	DynamoDB dynamoDB = new DynamoDB(dbClient);
    	Table table = dynamoDB.getTable(TABLENAME);
    	String partitionKey = "ALL";
    	
    	long twoWeeksAgoMilli = (new Date()).getTime() - (15L*24L*60L*60L*1000L);
        Date twoWeeksAgo = new Date();
        twoWeeksAgo.setTime(twoWeeksAgoMilli);
        SimpleDateFormat dateFormatter = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'");
        dateFormatter.setTimeZone(TimeZone.getTimeZone("UTC"));
        String twoWeeksAgoStr = dateFormatter.format(twoWeeksAgo);
    	
        QuerySpec spec = new QuerySpec()
        .withKeyConditionExpression("Id = :v_id and CreatedDt > :v_date")
        .withValueMap(new ValueMap()
            .withString(":v_id", partitionKey).withString(":v_date",twoWeeksAgoStr.toString()))
        .withConsistentRead(true)
        .withScanIndexForward(false); //desc
        
    	
    	spec.setMaxResultSize(10);
    	
    	List<PostBean> postList = new ArrayList<PostBean>();
    	ItemCollection<QueryOutcome> items = table.query(spec);
    	Iterator<Item> iterator = items.iterator();
    	while (iterator.hasNext()) {
    	    //System.out.println(iterator.next().toJSONPretty());
    	    Item item=iterator.next();
    	    PostBean postBean =new PostBean();
    	    postBean.setTitle(item.getString("Title"));
    	    postBean.setId(item.getString("Id"));
    	    BigDecimal votes= item.getNumber("Votes");
    	    if(votes==null)
    	    {
    	    	votes= new BigDecimal(1);
    	    }
    	    //postBean.setImgSrc("https://"+BUCKETNAME+".s3.amazonaws.com/img/"+item.getString("Filename"));
    	    //dydbp5ibukk9r.cloudfront.net
    	    postBean.setImgSrc("https://dydbp5ibukk9r.cloudfront.net/img/"+item.getString("Filename"));
    	    

    	    postBean.setVotes(votes.doubleValue());
    	    postBean.setCreatedDt(item.getString("CreatedDt"));
    	    postList.add(postBean);    	    
    	}
    	model.addAttribute("postList",postList);
    	//static DynamoDB dynamoDB = new DynamoDB(new AmazonDynamoDBClient(
        //        new ProfileCredentialsProvider()));
    	//s3client.createBucket(BUCKETNAME);
    	//https://rudygunawanbucket.s3.amazonaws.com/img/img1.jpg
        return "trending";
    }
    
    @RequestMapping(value="/upload", method=RequestMethod.POST)
    public String upload(@RequestParam("title") String title,@RequestParam("file") MultipartFile file,RedirectAttributes redirectAttributes)
    {
    	  log.info("file"+file);
    	  if(StringUtils.isEmpty(title))
    	  {
    		  redirectAttributes.addFlashAttribute("error", "Title can't be empty");
			  return "redirect:/upload";
    	  }
    	  
    	  if (!file.isEmpty() ) 	
    	  {
    		  try{
    			 
    			  String ext = FilenameUtils.getExtension(file.getOriginalFilename());
    			  if(!(ext.equalsIgnoreCase("jpg")||ext.equalsIgnoreCase("png")||ext.equalsIgnoreCase("mp4")))
    			  {
    				  redirectAttributes.addFlashAttribute("error", "Only upload jpg, png or mp4");
    				  return "redirect:/upload";
    			  }
    			  
    			  String newFilename="RY_"+UUID.randomUUID().toString()+"."+ext;
    			  BufferedOutputStream stream = new BufferedOutputStream(new FileOutputStream("/tmp/" + newFilename));
                  FileCopyUtils.copy(file.getInputStream(), stream);
                  stream.close();
                  insertDoc(newFilename);
                  insertRecord(title,newFilename);
              }catch(Exception e)
    		  {
    			  e.printStackTrace();
    			  log.error("not able to upload:"+e.getMessage(),e);
    			  redirectAttributes.addFlashAttribute("error", "Not able to upload :"+e.getMessage());
    			  return "redirect:/upload";	
    		  }
    	  }else
    	  {
    		  log.error("empty");
    		  redirectAttributes.addFlashAttribute("error", "Not able to upload : empty file");
    		  return "redirect:/upload";
				
    	  }
    	  redirectAttributes.addFlashAttribute("message", "Upload success!");
			
    	  return "redirect:/";	
    	
       
    }
    
    private void insertDoc(String fileName)
    {
    	AmazonS3 s3client = new AmazonS3Client(credentials);
    	s3client.setRegion(Region.getRegion(Regions.AP_SOUTHEAST_1));
    	File file =new File("/tmp/"+fileName);
    	if(!file.exists())
    	{
    		System.out.println(">>>>>not existtt");
    	}
    	ByteArrayInputStream input = new ByteArrayInputStream("Hello World!".getBytes());
    	//s3client.putObject(BUCKETNAME, "/img/hello.txt", file, new ObjectMetadata());
    	//s3client.putObject(BUCKETNAME,"img/"+fileName,file);
    	
    	PutObjectResult result=s3client.putObject(new PutObjectRequest(BUCKETNAME, "img/"+fileName, file).withCannedAcl(CannedAccessControlList.PublicRead));
    	//System.out.println(result);
    }
    
     
    private void insertRecord(String title, String filename)
    {
    	AmazonDynamoDBClient dbClient = new AmazonDynamoDBClient(credentials);
    	dbClient.setRegion(Region.getRegion(Regions.AP_SOUTHEAST_1));
    	DynamoDB dynamoDB = new DynamoDB(dbClient);
    	String Id = "ALL";
    	System.out.println("try to get table Post");
	    Table table = dynamoDB.getTable(TABLENAME);
        Item item = new Item().withPrimaryKey("Id",Id)
        				.withString("Title", title)
        				.withString("Filename",filename)
        				.withNumber("Votes", new BigDecimal(1))
	    			    .withString("CreatedDt",getUTCTimeAsString());
	    table.putItem(item);
	}
    
    @RequestMapping("/delete")
    public String delete()
    {
    	AmazonDynamoDBClient dbClient = new AmazonDynamoDBClient(credentials);
    	dbClient.setRegion(Region.getRegion(Regions.AP_SOUTHEAST_1));
    	DynamoDB dynamoDB = new DynamoDB(dbClient);
    	return "deleted";
    }
    
    private static String getUTCTimeAsString()
    {
    	//Date (as ISO8601 millisecond-precision string, shifted to UTC)
        //20140710T160939.473Z
    	DateTime dt = new DateTime(DateTimeZone.UTC);
        DateTimeFormatter fmt = ISODateTimeFormat.basicDateTime();
        return  fmt.print(dt); 
    }
}
