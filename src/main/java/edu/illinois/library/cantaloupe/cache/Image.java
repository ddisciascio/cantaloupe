package edu.illinois.library.cantaloupe.cache;

import edu.illinois.library.cantaloupe.request.Parameters;

import javax.jdo.annotations.Column;
import javax.jdo.annotations.IdGeneratorStrategy;
import javax.jdo.annotations.Index;
import javax.jdo.annotations.PersistenceCapable;
import javax.jdo.annotations.Persistent;
import javax.jdo.annotations.PrimaryKey;
import javax.jdo.annotations.Unique;
import java.util.Date;

@PersistenceCapable(table = "cantaloupe_image_cache")
class Image {

    @PrimaryKey
    @Persistent(valueStrategy = IdGeneratorStrategy.INCREMENT)
    private long id;

    @Persistent @Index @Unique @Column(jdbcType = "VARCHAR", length = 4096)
    private String parameters;

    @Persistent @Column(jdbcType = "BINARY")
    private byte[] image;

    @Persistent(customValueStrategy = "timestamp")
    @Index @Column(name = "last_modified", jdbcType = "TIMESTAMP")
    private Date lastModified;

    public Parameters getParameters() {
        return Parameters.fromUri(parameters);
    }

    public void setParameters(Parameters parameters) {
        this.parameters = parameters.toString();
    }

    public byte[] getImage() {
        return image;
    }

    public void setImage(byte[] image) {
        this.image = image;
    }

    public Date getLastModified() {
        return lastModified;
    }

    public void setLastModifieed(Date lastModified) {
        this.lastModified = lastModified;
    }

}
