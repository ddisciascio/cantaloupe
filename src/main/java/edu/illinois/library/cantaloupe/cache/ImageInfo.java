package edu.illinois.library.cantaloupe.cache;

import javax.jdo.annotations.Column;
import javax.jdo.annotations.IdGeneratorStrategy;
import javax.jdo.annotations.Index;
import javax.jdo.annotations.PersistenceCapable;
import javax.jdo.annotations.Persistent;
import javax.jdo.annotations.PrimaryKey;
import javax.jdo.annotations.Unique;
import java.util.Date;

@PersistenceCapable(table = "cantaloupe_info_cache")
class ImageInfo {

    @PrimaryKey
    @Persistent(valueStrategy= IdGeneratorStrategy.INCREMENT)
    private long id;

    @Persistent @Index @Unique @Column(jdbcType = "VARCHAR", length = 4096)
    private String identifier;

    @Persistent
    private int width;

    @Persistent
    private int height;

    @Persistent(customValueStrategy = "timestamp")
    @Index @Column(name = "last_modified", jdbcType = "TIMESTAMP")
    private Date lastModified;

    public int getHeight() {
        return height;
    }

    public void setHeight(int height) {
        this.height = height;
    }

    public String getIdentifier() {
        return identifier;
    }

    public void setIdentifier(String identifier) {
        this.identifier = identifier;
    }

    public Date getLastModified() {
        return lastModified;
    }

    public void setLastModified(Date lastModified) {
        this.lastModified = lastModified;
    }

    public int getWidth() {
        return width;
    }

    public void setWidth(int width) {
        this.width = width;
    }

}
