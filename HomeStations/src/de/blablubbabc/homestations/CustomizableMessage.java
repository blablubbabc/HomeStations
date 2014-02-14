package de.blablubbabc.homestations;

public class CustomizableMessage {
	public Message id;
	public String text;
	public String notes;

	public CustomizableMessage(Message id, String text, String notes) {
		this.id = id;
		this.text = text;
		this.notes = notes;
	}
}