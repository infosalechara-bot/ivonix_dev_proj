package com.ivonix.pulse.ontology.meet;
import org.springframework.http.ResponseEntity; import org.springframework.security.core.Authentication; import org.springframework.web.bind.annotation.*; import org.springframework.web.multipart.MultipartFile; import java.util.*;
@RestController @RequestMapping("/api/meet") public class MeetController{private final MeetService s;public MeetController(MeetService x){s=x;}private UUID u(Authentication a){return UUID.fromString(a.getName());}
@PostMapping("/rooms") public Object create(@RequestBody RoomRequest r,Authentication a){return Map.of("roomId",s.create(r.organizationId(),r.name(),u(a),r.options()==null?Map.of():r.options()));}
@PostMapping("/rooms/{roomId}/join") public Object join(@PathVariable UUID roomId,Authentication a){return s.join(roomId,u(a));}
@PostMapping("/rooms/{roomId}/leave") public Map<String,Boolean> leave(@PathVariable UUID roomId,Authentication a){s.leave(roomId,u(a));return Map.of("ok",true);}
@PostMapping("/rooms/{roomId}/signal") public Map<String,Boolean> signal(@PathVariable UUID roomId,@RequestBody SignalRequest r,Authentication a){s.signal(roomId,u(a),r.recipientId(),r.type(),r.payload());return Map.of("ok",true);}
@GetMapping("/turn") public Object turn(Authentication a){return s.turn(u(a));}
@PostMapping("/rooms/{roomId}/recording/start") public Object start(@PathVariable UUID roomId,Authentication a){return Map.of("recordingId",s.startRecording(roomId,u(a)));}
@PostMapping(value="/recordings/{recordingId}/upload",consumes="multipart/form-data") public Map<String,Boolean> upload(@PathVariable UUID recordingId,@RequestPart("file") MultipartFile file,Authentication a){s.uploadRecording(recordingId,u(a),file);return Map.of("ok",true);}
public record RoomRequest(UUID organizationId,String name,Map<String,Object> options){} public record SignalRequest(UUID recipientId,String type,Map<String,Object> payload){}
}