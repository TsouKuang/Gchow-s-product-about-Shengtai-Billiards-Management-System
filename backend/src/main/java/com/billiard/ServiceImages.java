package com.billiard;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.*;
import java.nio.file.*;
import java.util.*;
import javax.imageio.ImageIO;
@RestController
public class ServiceImages {
 final Api api; final Path directory;
 ServiceImages(Api api,@Value("${app.upload-dir:./uploads}") String directory){this.api=api;this.directory=Path.of(directory).toAbsolutePath().normalize();}
 @PostMapping("/api/admin/service-images") Object upload(@RequestHeader(value="Authorization",defaultValue="")String header,@RequestParam("file") MultipartFile file)throws Exception{
  api.auth(header,true);
  if(file.isEmpty()||file.getSize()>5*1024*1024)throw new IllegalArgumentException("请选择不超过 5 MB 的图片");
  try(var stream=ImageIO.createImageInputStream(file.getInputStream())){
   var readers=ImageIO.getImageReaders(stream);if(!readers.hasNext())throw new IllegalArgumentException("请选择 PNG、JPG 或 WEBP 图片");
   var reader=readers.next();try{reader.setInput(stream);int w=reader.getWidth(0),h=reader.getHeight(0);if(w<1||h<1||w>6000||h>6000||(long)w*h>16000000)throw new IllegalArgumentException("图片尺寸过大，请使用 1600 万像素以内图片");
    var image=reader.read(0);Files.createDirectories(directory);String name=UUID.randomUUID()+".png";ImageIO.write(image,"png",directory.resolve(name).toFile());return Map.of("path","/assets/uploads/"+name);
   }finally{reader.dispose();}
  }
 }
 @ExceptionHandler(Api.AuthError.class) org.springframework.http.ResponseEntity<?> authError(Api.AuthError e){return api.authError(e);}
 @ExceptionHandler(IllegalArgumentException.class) org.springframework.http.ResponseEntity<?> invalid(IllegalArgumentException e){return api.bad(e);}
}
@Configuration
class ServiceImageResources implements WebMvcConfigurer {
 final String directory;ServiceImageResources(@Value("${app.upload-dir:./uploads}")String directory){this.directory=Path.of(directory).toAbsolutePath().normalize().toUri().toString();}
 public void addResourceHandlers(ResourceHandlerRegistry registry){registry.addResourceHandler("/assets/uploads/**").addResourceLocations(directory.endsWith("/")?directory:directory+"/");}
}
