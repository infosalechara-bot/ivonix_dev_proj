// Change this line in KeyController.java:
@PostMapping("/create") 
public ResponseEntity<?> create(@RequestBody KeyService.KeyRequest r, Authentication a){
    UUID org=UUID.fromString(r.organizationId());
    // UUID userId = user(a); // COMMENT THIS OUT
    UUID userId = UUID.fromString("00000000-0000-0000-0000-000000000001"); // TEMPORARY TEST USER ID
    return ResponseEntity.ok(service.create(userId, org, r));
}
