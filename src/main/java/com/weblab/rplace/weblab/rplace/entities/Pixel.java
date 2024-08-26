package com.weblab.rplace.weblab.rplace.entities;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;


@Data
@Entity
@AllArgsConstructor
@NoArgsConstructor
@Builder
@Table(name="pixels")
public class Pixel {
	
	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	@Column(name = "id")
	private int id;
	
	@Column(name="x")
	private int x;
	
	@Column(name="y")
	private int y;
	
	@Column(name="color")
	private String color;

}
