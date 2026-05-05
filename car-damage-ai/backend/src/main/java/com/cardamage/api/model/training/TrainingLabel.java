package com.cardamage.api.model.training;

public class TrainingLabel {
    private String type;
    private String damageType;
    private String part;
    private String severity;
    private String note;
    private int x;
    private int y;
    private int w;
    private int h;

    public TrainingLabel() {
    }

    public TrainingLabel(String type, String damageType, String part, String severity, String note, int x, int y, int w, int h) {
        this.type = type;
        this.damageType = damageType;
        this.part = part;
        this.severity = severity;
        this.note = note;
        this.x = x;
        this.y = y;
        this.w = w;
        this.h = h;
    }

    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }

    public String getDamageType() {
        return damageType;
    }

    public void setDamageType(String damageType) {
        this.damageType = damageType;
    }

    public String getPart() {
        return part;
    }

    public void setPart(String part) {
        this.part = part;
    }

    public String getSeverity() {
        return severity;
    }

    public void setSeverity(String severity) {
        this.severity = severity;
    }

    public String getNote() {
        return note;
    }

    public void setNote(String note) {
        this.note = note;
    }

    public int getX() {
        return x;
    }

    public void setX(int x) {
        this.x = x;
    }

    public int getY() {
        return y;
    }

    public void setY(int y) {
        this.y = y;
    }

    public int getW() {
        return w;
    }

    public void setW(int w) {
        this.w = w;
    }

    public int getH() {
        return h;
    }

    public void setH(int h) {
        this.h = h;
    }
}
