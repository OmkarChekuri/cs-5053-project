package engine.graph.particles;

import org.joml.Vector3f;
import engine.graph.Mesh;
import engine.graph.Texture;
import engine.items.GameItem;
import java.util.Random;

public class Particle extends GameItem {

	private long updateTextureMillis;

	private Vector3f speed;
	
	float x0,x1,x2,x3;
	float y0,y1,y2,y3;
	float z0,z1,z2,z3;
	private float count = 0;
    private float addedSpeed = 0;
    Random rn;

	/**
	 * Time to live for particle in milliseconds.
	 */
	private long ttl;

	private int animFrames;

	public Particle(Mesh mesh, Vector3f speed, long ttl, long updateTextureMillis) {
		super(mesh);
		this.speed = new Vector3f(speed);
		this.ttl = ttl;
		this.updateTextureMillis = updateTextureMillis;
		Texture texture = this.getMesh().getMaterial().getTexture();
		this.animFrames = texture.getNumCols() * texture.getNumRows();
	}

	public Particle(Particle baseParticle) {
		super(baseParticle.getMesh());
		Vector3f aux = baseParticle.getPosition();
		setPosition(aux.x, aux.y, aux.z);
		x0=aux.x;		x1=aux.x-0.5f;		x2=aux.x-0.7f;	x3=aux.x-0.5f;
		y0=aux.y; 		y1=aux.y+121.5f;	y2=aux.y+51.5f;	y3=aux.y;
		z0=aux.z;		z1=aux.z;			z2=aux.z;		z3=aux.z;
		setRotation(baseParticle.getRotation());
		setScale(baseParticle.getScale());
		this.speed = new Vector3f(baseParticle.speed);
		this.ttl = baseParticle.geTtl();
		this.updateTextureMillis = baseParticle.getUpdateTextureMillis();
		this.animFrames = baseParticle.getAnimFrames();
	}
	
	public Particle(Mesh mesh, Vector3f speed, long ttl) {
        super(mesh);
        this.speed = new Vector3f(speed);
        this.ttl = ttl;
    }
	
	public Particle(Particle baseParticle, Random rn) {
        this(baseParticle);
        this.rn = rn;
        int xRand = rn.nextInt(3);
        float sign;
        if(xRand==0) sign=-1.0f;
        else if(xRand==1) sign=0.0f;
        else sign=1.0f;
        z1+= sign * rn.nextFloat()*5; 
        sign = rn.nextFloat() > 0.5d ? -1.0f : 1.0f;
        z2+= sign * rn.nextFloat()*5; z3+= sign * rn.nextFloat()*5;
        sign = rn.nextFloat() > 0.5d ? -1.0f : 1.0f;
    	y1+= sign * rn.nextFloat()*2; y2+= sign * rn.nextFloat()*2;
    	sign = rn.nextFloat() > 0.5d ? -1.0f : 1.0f;
    	x1+= sign * rn.nextFloat(); x2+= sign * rn.nextFloat(); x3+= sign * rn.nextFloat();
    	addedSpeed = rn.nextFloat()*1.5f + 1;
    }

	public int getAnimFrames() {
		return animFrames;
	}

	public Vector3f getSpeed() {
		return speed;
	}

	public long getUpdateTextureMillis() {
		return updateTextureMillis;
	}

	public void setSpeed(Vector3f speed) {
		this.speed = speed;
	}

	public long geTtl() {
		return ttl;
	}

	public void setTtl(long ttl) {
		this.ttl = ttl;
	}

	public void setUpdateTextureMills(long updateTextureMillis) {
		this.updateTextureMillis = updateTextureMillis;
	}

	public long updateTtl(long elapsedTime) {
        this.ttl -= elapsedTime;
        return this.ttl;
    }
	
	public void updatePosition(long deltaTime) {
    	count+=addedSpeed;
    	if(count > 280) count=280;
    	float t = count/280.0f;
    	float x= bez(0,t)*x0+bez(1,t)*x1+bez(2,t)*x2+bez(3,t)*x3;
		float y =bez(0,t)*y0+bez(1,t)*y1+bez(2,t)*y2+bez(3,t)*y3;
		float z =bez(0,t)*z0+bez(1,t)*z1+bez(2,t)*z2+bez(3,t)*z3;
	    setPosition(x, y, z); 
    }

	// Cubic Bezier function
	private float bez(int i, float t) {
		switch (i) {
		case 0:
			return (1 - t) * (1 - t) * (1 - t);
		case 1:
			return 3 * t * (1 - t) * (1 - t);
		case 2:
			return 3 * t * t * (1 - t);
		case 3:
			return t * t * t;
		}
		return 0;
	}

}
