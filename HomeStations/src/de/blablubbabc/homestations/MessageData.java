package de.blablubbabc.homestations;

class MessageData {

	public final Message id;
	public final String text;
	public final String notes;

	MessageData(Message id, String text, String notes) {
		this.id = id;
		this.text = text;
		this.notes = notes;
	}
}