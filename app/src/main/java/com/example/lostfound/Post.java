package com.lostandfound;

public class Post {
    private String id;
    private String type;       // "Lost" or "Found"
    private String description;
    private String location;
    private String imageUrl;

    public Post() { }

    public Post(String id, String type, String description, String location, String imageUrl) {
        this.id = id;
        this.type = type;
        this.description = description;
        this.location = location;
        this.imageUrl = imageUrl;
    }

    // Getters & setters
    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getType() { return type; }
    public void setType(String type) { this.type = type; }

    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }

    public String getLocation() { return location; }
    public void setLocation(String location) { this.location = location; }

    public String getImageUrl() { return imageUrl; }
    public void setImageUrl(String imageUrl) { this.imageUrl = imageUrl; }

}
