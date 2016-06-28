package com.demo;

public class PostBean {
	private String Id;
	private String title;
	private String imgSrc;

	private Double votes;
	private String createdDt;
	
	public String getImgSrc() {
		return imgSrc;
	}
	public void setImgSrc(String imgSrc) {
		this.imgSrc = imgSrc;
	}
	public String getId() {
		return Id;
	}
	public void setId(String id) {
		Id = id;
	}
	public String getTitle() {
		return title;
	}
	public void setTitle(String title) {
		this.title = title;
	}
	public Double getVotes() {
		return votes;
	}
	public void setVotes(Double votes) {
		this.votes = votes;
	}
	public String getCreatedDt() {
		return createdDt;
	}
	public void setCreatedDt(String createdDt) {
		this.createdDt = createdDt;
	}

}
