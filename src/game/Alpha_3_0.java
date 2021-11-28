package game;

import org.joml.Intersectionf;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import static org.lwjgl.glfw.GLFW.*;

import java.awt.geom.Point2D;
import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;
import engine.IGameLogic;
import engine.MouseInput;
import engine.Scene;
import engine.SceneLight;
import engine.Window;
import engine.graph.Camera;
import engine.graph.Mesh;
import engine.graph.Renderer;
import engine.graph.lights.DirectionalLight;
import engine.graph.lights.PointLight;
import engine.graph.weather.Fog;
import engine.items.GameItem;
import engine.items.SkyBox;
import engine.loaders.assimp.StaticMeshesLoader;
import engine.graph.Material;
import engine.graph.Texture;
import engine.graph.particles.FlowParticleEmitter;
import engine.graph.particles.Particle;
import engine.loaders.obj.OBJLoader;

public class Alpha_3_0 implements IGameLogic 
{
    private final Renderer renderer;

    private final Camera camera;

    private Scene scene;

    /**
     * Game state variables
     */
    private boolean firstTime;
    private boolean sceneChanged;
    private boolean jumping;
    private boolean falling;
    private boolean landed;
    private boolean dead;
    private boolean victoryAchieved;
    private boolean moving;
    private boolean facingLeft;
    private boolean leftFootStepping;

    private Vector3f pointLightPos;
    
    /*
     * Game Items for the level
     */
    private GameItem human;
    private GameItem humanWalkingLeftAndFacingLeft;
    private GameItem humanWalkingLeftAndFacingRight;
    private GameItem humanWalkingRightAndFacingLeft;
    private GameItem humanWalkingRightAndFacingRight;
    private GameItem humanStandingAndFacingLeft;
    private GameItem humanStandingAndFacingRight;
    private GameItem gameOverText;
    private GameItem victoryText;
    private GameItem victoryPlatform; 
	private GameItem bottomCannonBall; 
	private GameItem midCannonBall; 
	private GameItem topCannonBall; 

    private List<GameItem> platforms;
    private List<GameItem> cannons;
    private List<Vector3f> bottomCannonBallTrajectory;
    private List<Vector3f> midCannonBallTrajectory;
    private List<Vector3f> topCannonBallTrajectory;
    
	private ArrayList<Point2D.Double> bottomCannonBallBezierPoints = new ArrayList<Point2D.Double>();
	private ArrayList<Point2D.Double> midCannonBallBezierPoints = new ArrayList<Point2D.Double>();
	private ArrayList<Point2D.Double> topCannonBallBezierPoints = new ArrayList<Point2D.Double>();

	private int bottomCannonBallTrajectoryIndex = 0;
	private int midCannonBallTrajectoryIndex = 0;
	private int topCannonBallTrajectoryIndex = 0;
    private int swapLeg = STEP_BUFFER;

    private float jumpDistance; 

    private final static float EDGE_TOLERANCE = .45f;				// Error tolerance for determining if on edge of platform
    private final static float BOUNDING_RADIUS_TOLERANCE = .95f;	// Decrease bounding sphere size by 5% (95 - 100 = -5)
    private final static float MOVEMENT_SPEED = .45f;				// Left and right movement speed
    private final static float JUMP_HEIGHT = 16f;					// How high a jump is
    private final static float JUMP_SPEED = .5f;					// Rate to reach apex of jump
    private final static float FALL_SPEED = .5f;					// Rate of falling for game
    private final static float HUMAN_PLATFORM_HEIGHT_OFFSET = 1.85f;// Y offset need to place human feet on top of platform
    private final static float HUMAN_HIT_BOX_ERROR_TOLERANCE = .75f;// 1 is normal hit box size, a value greater than 1 increases the hitbox side, and a value less than 1 decreases it
    private final static int STEP_BUFFER = 12;
    
    private FlowParticleEmitter particleEmitter;

    public Alpha_3_0() 
    {
        renderer = new Renderer();
        camera = new Camera();
        firstTime = true;
        jumping = false;
        falling = false;
        landed = true;
        platforms = new LinkedList<GameItem>();
        
        //Create cannons
        cannons = new LinkedList<GameItem>();
		bottomCannonBallTrajectory = new LinkedList<>();
		midCannonBallTrajectory = new LinkedList<>();
		topCannonBallTrajectory = new LinkedList<>();
        
        //Create cannon ball trajectories
		calculateBottomCannonBallBezierPoints();
		calculateMidCannonBallBezierPoints();
		calculateTopCannonBallBezierPoints();	
    }
    
    private java.awt.geom.Point2D.Double getBezierPoints(java.util.List<Point2D.Double> Point, double t) 
    {
		double x = Math.pow(1 - t, 3) * Point.get(0).x + 3 * (t) * Math.pow(1 - t, 2) * Point.get(1).x
				+ 3 * (1 - t) * t * t * Point.get(2).x + t * t * t * Point.get(3).x;
		double y = Math.pow(1 - t, 3) * Point.get(0).y + 3 * (t) * Math.pow(1 - t, 2) * Point.get(1).y
				+ 3 * (1 - t) * t * t * Point.get(2).y + t * t * t * Point.get(3).y;
		Point2D.Double P = new Point2D.Double(x, y);
		return P;
	}
    
    @Override
    public void init(Window window) throws Exception 
    {
    	glfwSetInputMode(window.getWindowHandle(), GLFW_CURSOR, GLFW_CURSOR_HIDDEN);
        renderer.init(window);
        scene = new Scene();
        
        createLevel();
        
        // Shadows
        scene.setRenderShadows(true);

        // Fog
        Vector3f fogColour = new Vector3f(0.5f, 0.5f, 0.5f);
        scene.setFog(new Fog(false, fogColour, 0.02f));

        // Setup  SkyBox
        float skyBoxScale = 100.0f;
        SkyBox skyBox = new SkyBox("models/skybox.obj", "textures/sky2.jpg");
        skyBox.setScale(skyBoxScale);
        scene.setSkyBox(skyBox);

        // Setup Lights
        setupLights();
        
        // Setup camera
        camera.getPosition().x =  -100;
        camera.getPosition().z =  85;
        camera.getPosition().y =  50;
        camera.moveRotation(0, 90, 0);
    }

    private void setupLights() 
    {
        SceneLight sceneLight = new SceneLight();
        scene.setSceneLight(sceneLight);

        // Ambient Light
        sceneLight.setAmbientLight(new Vector3f(0.3f, 0.3f, 0.3f));
        sceneLight.setSkyBoxLight(new Vector3f(1.0f, 1.0f, 1.0f));

        // Directional Light
        float lightIntensity = 1.0f;
        Vector3f lightDirection = new Vector3f(0, 0, 1);
        DirectionalLight directionalLight = new DirectionalLight(new Vector3f(1, 1, 1), lightDirection, lightIntensity);
        sceneLight.setDirectionalLight(directionalLight);

        pointLightPos = new Vector3f(0.0f, 25.0f, 0.0f);
        Vector3f pointLightColour = new Vector3f(0.0f, 1.0f, 0.0f);
        PointLight.Attenuation attenuation = new PointLight.Attenuation(1, 0.0f, 0);
        PointLight pointLight = new PointLight(pointLightColour, pointLightPos, lightIntensity, attenuation);
        sceneLight.setPointLightList( new PointLight[] {pointLight});
    }

    @Override
    public void input(Window window, MouseInput mouseInput)
    {
        sceneChanged = false;
        moving = false;
        if (!dead)
        {
			if (window.isKeyPressed(GLFW_KEY_A)||window.isKeyPressed(GLFW_KEY_LEFT)) 
			{
				sceneChanged = true;
				moving = true;
				human.getPosition().z += -MOVEMENT_SPEED;
				facingLeft = true;
			} 

			else if (window.isKeyPressed(GLFW_KEY_D) ||window.isKeyPressed(GLFW_KEY_RIGHT)) 
			{
				facingLeft = false;
				sceneChanged = true;
				moving = true;
				human.getPosition().z += MOVEMENT_SPEED;
			}

			//  Jump
			if (window.isKeyPressed(GLFW_KEY_SPACE) && landed) 
			{
				if (!jumping && !falling)
				{	
					jumping = true;
					jumpDistance = human.getPosition().y + JUMP_HEIGHT;
					landed = false;
				}
			}
			
			handleJumping();
			handleFalling();
			updateCameraHeight();
        }
        
        if (window.isKeyPressed(GLFW_KEY_R))
        {
        	resetGame();
        }
    }

    @Override
    public void update(float interval, MouseInput mouseInput, Window window) 
    {
        if (!falling && !jumping)
        {
        	checkIfStartedFalling();
        }
        
        if(!firstTime && hitByCannonBall())
        {
        	dead = true;
        }

		if (dead)
		{
			displayGameOver();
		}

		if (victoryAchieved)
		{
			displayVictory();
			particleEmitter.update((long) (interval * 1000));
		}

        // Update view matrix
        camera.updateViewMatrix();
    }

    @Override
    public void render(Window window) 
    {	
        if (firstTime) 
        {
            sceneChanged = true;
            firstTime = false;
        }

        if (!dead && !falling && !jumping && !victoryAchieved)
        {
        	renderMovement();
        }
    	bottomCannonBall.getPosition().set(bottomCannonBallTrajectory.get(bottomCannonBallTrajectoryIndex));
		bottomCannonBallTrajectoryIndex = ++bottomCannonBallTrajectoryIndex % bottomCannonBallTrajectory.size(); 

    	midCannonBall.getPosition().set(midCannonBallTrajectory.get(midCannonBallTrajectoryIndex));
		midCannonBallTrajectoryIndex = ++midCannonBallTrajectoryIndex % midCannonBallTrajectory.size(); 

    	topCannonBall.getPosition().set(topCannonBallTrajectory.get(topCannonBallTrajectoryIndex));
		topCannonBallTrajectoryIndex = ++topCannonBallTrajectoryIndex % topCannonBallTrajectory.size(); 
        renderer.render(window, camera, scene, sceneChanged);
    }

    @Override
    public void cleanup() 
    {
    	
        renderer.cleanup();

        scene.cleanup();
    }
    
    private boolean standingOnPlatform(GameItem platform, GameItem human)
    {
        Vector3f direction = new Vector3f(0,0, EDGE_TOLERANCE);
        boolean pastOrOnLeftEdge = Intersectionf.testRaySphere(human.getPosition(), direction, platform.getPosition(), 
        	   (float) Math.pow(platform.getScale()*platform.getMesh().getBoundingRadius()*BOUNDING_RADIUS_TOLERANCE, 2));

        direction = new Vector3f(0,0, -EDGE_TOLERANCE);
        boolean beforeOrOnRightEdge = Intersectionf.testRaySphere(human.getPosition(), direction, platform.getPosition(), 
        	   (float) Math.pow(platform.getScale()*platform.getMesh().getBoundingRadius()*BOUNDING_RADIUS_TOLERANCE, 2));
        
        return pastOrOnLeftEdge || beforeOrOnRightEdge;
    }
    
    private void createLevel() throws Exception
    {
    	createPlatforms();
    	createCannons();
    	createcannonBalls();
    	createHuman();
    	createGameOverText();
    	createVictoryText();
		setupParticleSystem();
    }
    
    private void createCannons() throws Exception
    {
    	// Cannon Model
		GameItem cannon;

		// Mesh for cannon facing right
		Mesh cannonMeshRight = OBJLoader.loadMesh("models/cannon/CannonRight.obj");
		cannonMeshRight.setBoundingRadius(.5f);
		Texture texture = new Texture("textures/iron.jpg", 2,1);
		Material material = new Material(texture, 1f);
		cannonMeshRight.setMaterial(material);
		// Mesh for cannon facing left
		Mesh cannonMeshLeft = OBJLoader.loadMesh("models/cannon/CannonLeft.obj");
		cannonMeshLeft.setBoundingRadius(.5f);
		cannonMeshLeft.setMaterial(material);
		
		// Bottom cannon
		cannon = new GameItem(cannonMeshRight);
		cannon.setScale(.5f);
		cannon.setPosition(0, 63, 80);
		cannons.add(cannon);
		
		// Mid cannon
		cannon = new GameItem(cannonMeshLeft);
		cannon.setScale(.5f);
		cannon.setPosition(0, 123, 80);
		cannons.add(cannon);
		
		// Top cannon
		cannon = new GameItem(cannonMeshRight);
		cannon.setScale(.5f);
		cannon.setPosition(0, 228, 80);
		cannons.add(cannon);
		
		GameItem[] cannonsArray = cannons.toArray(new GameItem[0]);
        scene.setGameItems(cannonsArray);
    }    

    public void calculateBottomCannonBallBezierPoints()
    {
		ArrayList<Point2D.Double> ballControlPoints = new ArrayList<Point2D.Double>();
        ballControlPoints.add(new Point2D.Double(0.00, 0.00));
		ballControlPoints.add(new Point2D.Double(8.00, 4.00));
		ballControlPoints.add(new Point2D.Double(17.00, 2.00));
		ballControlPoints.add(new Point2D.Double(30.00, -18.00));
		for (double t2 = 0.0; t2 < 1.0; t2 += 0.01) 
		{
			Point2D.Double P1 = getBezierPoints(ballControlPoints, t2);
			bottomCannonBallBezierPoints.add(P1);
		}

		ballControlPoints.clear();

		ballControlPoints.add(new Point2D.Double(30.00, -18.00));
		ballControlPoints.add(new Point2D.Double(36.00, -10.00));
		ballControlPoints.add(new Point2D.Double(47.00, -5.00));
		ballControlPoints.add(new Point2D.Double(65.00, -24.00));
		ballControlPoints.add(new Point2D.Double(80.00, -45.00));
		ballControlPoints.add(new Point2D.Double(101.00, -75.00));
		ballControlPoints.add(new Point2D.Double(114.00, -145.00));
		for (double t3 = 0.0; t3 < 2.0; t3 += 0.01) 
		{
			Point2D.Double P2 = getBezierPoints(ballControlPoints, t3);
			bottomCannonBallBezierPoints.add(P2);
		}
    }
    
	public void calculateMidCannonBallBezierPoints() 
	{
		ArrayList<Point2D.Double> ballControlPoints = new ArrayList<Point2D.Double>();
		ballControlPoints.add(new Point2D.Double(0.00, 0.00));
		ballControlPoints.add(new Point2D.Double(-8.00, 4.00));
		ballControlPoints.add(new Point2D.Double(-17.00, 2.00));
		ballControlPoints.add(new Point2D.Double(-30.00, -18.00));
		for (double t2 = 0.0; t2 < 1.0; t2 += 0.01) 
		{
			Point2D.Double P1 = getBezierPoints(ballControlPoints, t2);
			midCannonBallBezierPoints.add(P1);
		}

		ballControlPoints.clear();

		ballControlPoints.add(new Point2D.Double(-30.00, -18.00));
		ballControlPoints.add(new Point2D.Double(-36.00, -10.00));
		ballControlPoints.add(new Point2D.Double(-47.00, -5.00));
		ballControlPoints.add(new Point2D.Double(-65.00, -24.00));
		ballControlPoints.add(new Point2D.Double(-80.00, -45.00));
		ballControlPoints.add(new Point2D.Double(-101.00, -75.00));
		ballControlPoints.add(new Point2D.Double(-114.00, -145.00));

		for (double t3 = 0.0; t3 < 2.0; t3 += 0.01) 
		{
			Point2D.Double P2 = getBezierPoints(ballControlPoints, t3);
			midCannonBallBezierPoints.add(P2);
		}
	}
    
    
    public void calculateTopCannonBallBezierPoints()
    {
		ArrayList<Point2D.Double> ballControlPoints = new ArrayList<Point2D.Double>();
        //first bounce
    	ballControlPoints.clear();
        ballControlPoints.add(new Point2D.Double(0.00, 0.00));
		ballControlPoints.add(new Point2D.Double(15.00, 6.00));
		ballControlPoints.add(new Point2D.Double(27.00, 2.00));
		ballControlPoints.add(new Point2D.Double(40.00, -33.00));
		for (double t1 = 0.0; t1 < 1.0; t1 += 0.01) 
		{
			Point2D.Double P1 = getBezierPoints(ballControlPoints, t1);
			topCannonBallBezierPoints.add(P1);
		}

		ballControlPoints.clear();

		ballControlPoints.add(new Point2D.Double(40.00, -33.00));
		ballControlPoints.add(new Point2D.Double(57.00, -5.00));
		ballControlPoints.add(new Point2D.Double(65.00, -5.00));
		
		ballControlPoints.add(new Point2D.Double(75.00, -47.00));
				
		for (double t3 = 0.0; t3 < 1.0; t3 += 0.01) 
		{
			Point2D.Double P3 = getBezierPoints(ballControlPoints, t3);
			topCannonBallBezierPoints.add(P3);
		}

		ballControlPoints.clear();
		
		ballControlPoints.add(new Point2D.Double(75.00, -47.00));
		ballControlPoints.add(new Point2D.Double(95.00, -35.00));
		ballControlPoints.add(new Point2D.Double(115.00, -25.00));
		ballControlPoints.add(new Point2D.Double(135.00, -45.00));

		for (double t4 = 0.0; t4 < 1.5; t4 += 0.01) 
		{
			Point2D.Double P4 = getBezierPoints(ballControlPoints, t4);
			topCannonBallBezierPoints.add(P4);
		}
    }
    
    
	private void createcannonBalls() throws Exception 
	{
		Mesh cannonBallMesh = OBJLoader.loadMesh("models/ball/ball.obj");
		Texture texture = new Texture("textures/black.jpg", 2,1);
		Material material = new Material(texture, 1f);
		cannonBallMesh.setMaterial(material);

		bottomCannonBall = new GameItem(cannonBallMesh);
		bottomCannonBall.setScale(.5f);

		midCannonBall = new GameItem(cannonBallMesh);
		midCannonBall.setScale(.5f);

		topCannonBall = new GameItem(cannonBallMesh);
		topCannonBall.setScale(.5f);

		scene.setGameItems(new GameItem[] {bottomCannonBall, midCannonBall, topCannonBall});
		float y = 65;
		float x = 1;
		float z = 83;
		float trajectoryY;
		float trajectoryZ;

		for (int i = 0; i < bottomCannonBallBezierPoints.size(); i++) 
		{
			trajectoryZ = (float) bottomCannonBallBezierPoints.get(i).x;
			trajectoryY = (float) bottomCannonBallBezierPoints.get(i).y;

			trajectoryY += y ;
			trajectoryZ += z;
			bottomCannonBallTrajectory.add(new Vector3f(x, trajectoryY, trajectoryZ));
		}
				
		y = 124.5f;
		z = 80;
		for (int i = 0; i < midCannonBallBezierPoints.size(); i++) 
		{
			trajectoryZ = (float) midCannonBallBezierPoints.get(i).x;
			trajectoryY = (float) midCannonBallBezierPoints.get(i).y;

			trajectoryY += y ;
			trajectoryZ += z;
			midCannonBallTrajectory.add(new Vector3f(x, trajectoryY, trajectoryZ));
		}

		y = 229;
		z = 80;
		for (int i = 0; i < topCannonBallBezierPoints.size(); i++) 
		{
			trajectoryZ = (float) topCannonBallBezierPoints.get(i).x;
			trajectoryY = (float) topCannonBallBezierPoints.get(i).y;

			trajectoryY += y ;
			trajectoryZ += z;
			topCannonBallTrajectory.add(new Vector3f(x, trajectoryY, trajectoryZ));
		}
	}    

    private List<GameItem> createCannonPlatforms() throws Exception
    {
		GameItem platform;
		Mesh[] platformMesh = StaticMeshesLoader.load("models/platform/PP_steel_podium_platinum.obj", "models/platform");
		List<GameItem> platforms = new LinkedList<GameItem>();
		
		float y = 60;
		float z = 80;

		platform = new GameItem(platformMesh);
		platform.setScale(.125f);
		platform.setPosition(0,y, z);
		platforms.add(platform);

		y = 120;
		platform = new GameItem(platformMesh);
		platform.setScale(.125f);
		platform.setPosition(0,y, z);
		platforms.add(platform);

		y = 225;
		platform = new GameItem(platformMesh);
		platform.setScale(.125f);
		platform.setPosition(0,y, z);
		platforms.add(platform);
		
		return platforms;
    }    
    
    private void createPlatforms() throws Exception
    {
        float y =  0;
        GameItem last = new GameItem();
		GameItem platform;
		Mesh[] platformMesh = StaticMeshesLoader.load("models/platform/PP_steel_podium_platinum.obj", "models/platform");
		for(int numberOfPlatforms = 0; numberOfPlatforms < 5; ++numberOfPlatforms)
		{
			platform = new GameItem(platformMesh);
			platform.setScale(.125f);
			platform.setPosition(0, 0+y, numberOfPlatforms*40);
			platforms.add(platform);
			y+=15;
		}

		for(int numberOfPlatforms = 3; numberOfPlatforms >= 0; --numberOfPlatforms)
		{
			platform = new GameItem(platformMesh);
			platform.setScale(.125f);
			platform.setPosition(0, y, numberOfPlatforms*40f);
			platforms.add(platform);
			y+=15;
		}

		for(int numberOfPlatforms = 1; numberOfPlatforms < 5; ++numberOfPlatforms)
		{
			platform = new GameItem(platformMesh);
			platform.setScale(.125f);
			platform.setPosition(0, y, numberOfPlatforms*40);
			platforms.add(platform);
			y+=15;
		}

		for(int numberOfPlatforms = 3; numberOfPlatforms >= 0; --numberOfPlatforms)
		{
			platform = new GameItem(platformMesh);
			platform.setScale(.125f);
			platform.setPosition(0, y, numberOfPlatforms*40f);
			platforms.add(platform);
			last = platform;
		}

		y+=15;
		last.setPosition(0, y, 0);
		y+=15;
		for(int numberOfPlatforms = 1; numberOfPlatforms < 3; ++numberOfPlatforms)
		{
			platform = new GameItem(platformMesh);
			platform.setScale(.125f);
			platform.setPosition(0, y, numberOfPlatforms*40);
			platforms.add(platform);
			last = platform;
			y+=15;
		}
		

		platform = new GameItem(platformMesh);
		platform.setScale(.125f);
		platform.setPosition(0, y, 40);
		platforms.add(platform);

		y+=15;
		platform = new GameItem(platformMesh);
		platform.setScale(.125f);
		platform.setPosition(0, y, 80);
		victoryPlatform = platform;
		platforms.add(platform);

		platforms.addAll(createCannonPlatforms());

		GameItem[] platformsArray = platforms.toArray(new GameItem[0]);
        scene.setGameItems(platformsArray);
    }    

    private void createHuman() throws Exception
    {
		Mesh[] humanMesh = StaticMeshesLoader.load("models/human/basic-humanRight.obj", null);
		human = new GameItem(humanMesh);
		human.setPosition(0, HUMAN_PLATFORM_HEIGHT_OFFSET, 0);	
        scene.setGameItems(new GameItem[]{human});

		humanMesh = StaticMeshesLoader.load("models/human/basic-humanLLF_Left.obj", null);
		humanWalkingLeftAndFacingLeft = new GameItem(humanMesh);

		humanMesh = StaticMeshesLoader.load("models/human/basic-humanRLF_Left.obj", null);
		humanWalkingRightAndFacingLeft = new GameItem(humanMesh);

		humanMesh = StaticMeshesLoader.load("models/human/basic-humanRLF_Right.obj", null);
		humanWalkingLeftAndFacingRight = new GameItem(humanMesh);

		humanMesh = StaticMeshesLoader.load("models/human/basic-humanLLF_Right.obj", null);
		humanWalkingRightAndFacingRight = new GameItem(humanMesh);

		humanMesh = StaticMeshesLoader.load("models/human/basic-humanLeft.obj", null);
		humanStandingAndFacingLeft = new GameItem(humanMesh);

		humanMesh = StaticMeshesLoader.load("models/human/basic-humanRight.obj", null);
		humanStandingAndFacingRight = new GameItem(humanMesh);
    }

    private void createGameOverText() throws Exception
    {
		Mesh[] gameOverMesh = StaticMeshesLoader.load("models/game-over/game-over1.obj", "models/game-over");
		gameOverText = new GameItem(gameOverMesh);

		gameOverText.setPosition(-15, 70, 65);
		gameOverText.setScale(7);
    }
    
    private void createVictoryText() throws Exception
    {
		Mesh[] victoryTextMesh = StaticMeshesLoader.load("models/victory-text/victory-text1.obj", "models/victory-text");
		victoryText = new GameItem(victoryTextMesh);
		
		victoryText.setPosition(0, victoryPlatform.getPosition().y+25, 65);
		victoryText.setScale(7);
		//victoryText.setRotation(new Quaternionf(.7, -.45, .5, 1));
    }
    
    private void handleJumping()
    {
		if (jumping)
		{
			if (!falling)
			{
				human.getPosition().y += JUMP_SPEED;
				sceneChanged = true;
			}
			if (human.getPosition().y > jumpDistance)
			{
				jumping = false;
				falling = true;
			}
		}		
    }
    
    private void handleFalling()
    {
		if (falling)
		{
			sceneChanged = true;
			human.getPosition().y -= FALL_SPEED;
			checkLanding();
			if (falling && human.getPosition().y < 0)
			{
				dead = true;
			}
		}
    }

    private void checkLanding()
    {
		for(GameItem platform : platforms)
		{
			if (standingOnPlatform(platform, human))
			{
				falling = false;
				jumping = false;
				landed = true;
				scene.getGameMeshes().remove(gameOverText.getMesh());
				human.setPosition(human.getPosition().x, platform.getPosition().y+HUMAN_PLATFORM_HEIGHT_OFFSET, human.getPosition().z);
				if (platform == victoryPlatform)
				{
					victoryAchieved = true;
				}
				break;
			}
		}
    }
    
    private void resetGame()
    {
		dead = false;
		victoryAchieved = false;
		
		bottomCannonBallTrajectoryIndex = 0;
		midCannonBallTrajectoryIndex = 0;
		topCannonBallTrajectoryIndex = 0;
		
		particleEmitter.getParticles().clear();
		particleEmitter.setInitParticles();

		scene.getGameMeshes().clear();
		GameItem[] cannonsArray = cannons.toArray(new GameItem[0]);
        scene.setGameItems(cannonsArray);
        
		GameItem[] cannonBallsArray = new GameItem[] {bottomCannonBall, midCannonBall, topCannonBall};
        scene.setGameItems(cannonBallsArray);
        
		GameItem[] platformsArray = platforms.toArray(new GameItem[0]);
        scene.setGameItems(platformsArray);

		human.setPosition(0, HUMAN_PLATFORM_HEIGHT_OFFSET, 0);	
        scene.setGameItems(new GameItem[]{human});

		gameOverText.setPosition(-15, 70, 65);
    }
    
    private void updateCameraHeight()
    {
        if (human.getPosition().y + 25> camera.getPosition().y )
        {
        	camera.getPosition().y += 50;
        }
        if (human.getPosition().y < camera.getPosition().y && camera.getPosition().y != 50)
        {
        	camera.getPosition().y -= 50;
        }
    }
    
    private void checkIfStartedFalling()
    {
		falling = true;
		for(GameItem platform : platforms)
		{
			if (standingOnPlatform(platform, human))
			{
				falling = false;
				break;
			}
		}
    }
    
    private void displayGameOver()
    {
		scene.getGameMeshes().remove(human.getMesh());
		
		if (!scene.getGameMeshes().containsKey(gameOverText.getMesh()))
		{
			gameOverText.getPosition().y += camera.getPosition().y - 50f;
			scene.setGameItems(new GameItem[]{gameOverText});
		}
    }
    
    private void displayVictory()
    {
		scene.getGameMeshes().remove(human.getMesh());
		
		if (!scene.getGameMeshes().containsKey(victoryText.getMesh()))
		{
			scene.setGameItems(new GameItem[]{victoryText});
		}
    }
    
    private boolean hitByCannonBall()
    {
    	float humanBoundingSphereRadiusSquared = (float) Math.pow(human.getMesh().getBoundingRadius()*human.getScale(), 2);
    	Vector3f zeroVector = new Vector3f();
    	zeroVector.set(0);
    	if (Intersectionf.testRaySphere(bottomCannonBall.getPosition(), zeroVector, human.getPosition(), 
    			humanBoundingSphereRadiusSquared*HUMAN_HIT_BOX_ERROR_TOLERANCE))
    	{
    		return true;
    	}

    	if (Intersectionf.testRaySphere(midCannonBall.getPosition(), zeroVector, human.getPosition(), 
    			humanBoundingSphereRadiusSquared*HUMAN_HIT_BOX_ERROR_TOLERANCE))
    	{
    		return true;
    	}

    	if (Intersectionf.testRaySphere(topCannonBall.getPosition(), zeroVector, human.getPosition(),
    			humanBoundingSphereRadiusSquared*HUMAN_HIT_BOX_ERROR_TOLERANCE))
    	{
    		return true;
    	}
    	return false;
    }
    
    private void renderMovement()
    {
		if (moving && facingLeft)
		{
			Vector3f position = new Vector3f();
			position.set(human.getPosition());
			
			if (leftFootStepping && swapLeg > STEP_BUFFER)
			{
				scene.getGameMeshes().remove(human.getMesh());
				swapLeg = 0;
				leftFootStepping = !leftFootStepping;
				human = humanWalkingLeftAndFacingLeft;
				human.getPosition().set(position);
				scene.setGameItems(new GameItem[] {human});
			}
			else if (swapLeg > STEP_BUFFER)
			{
				scene.getGameMeshes().remove(human.getMesh());
				swapLeg = 0;
				leftFootStepping = !leftFootStepping;
				human = humanWalkingRightAndFacingLeft;
				human.getPosition().set(position);
				scene.setGameItems(new GameItem[] {human});
			}
			
			++swapLeg;
		}
		else if (moving)
		{
			Vector3f position = new Vector3f();
			position.set(human.getPosition());
			if (leftFootStepping && swapLeg > STEP_BUFFER)
			{
				scene.getGameMeshes().remove(human.getMesh());
				swapLeg = 0;
				leftFootStepping = !leftFootStepping;
				human = new GameItem(humanWalkingLeftAndFacingRight.getMeshes());
				human.getPosition().set(position);
				scene.setGameItems(new GameItem[] {human});
			}
			else if (swapLeg > STEP_BUFFER)
			{
				scene.getGameMeshes().remove(human.getMesh());
				swapLeg = 0;
				leftFootStepping = !leftFootStepping;
				human = new GameItem(humanWalkingRightAndFacingRight.getMeshes());
				human.getPosition().set(position);
				scene.setGameItems(new GameItem[] {human});
			}
			++swapLeg;
		}
		else
		{
			scene.getGameMeshes().remove(human.getMesh());
			Vector3f position = new Vector3f();
			position.set(human.getPosition());

			if (facingLeft)
			{
				human = new GameItem(humanStandingAndFacingLeft.getMeshes());
				human.getPosition().set(position);
				scene.setGameItems(new GameItem[] {human});
			}
			else
			{
				human = new GameItem(humanStandingAndFacingRight.getMeshes());
				human.getPosition().set(position);
				scene.setGameItems(new GameItem[] {human});
			}
		}
    }
    
    private void setupParticleSystem() throws Exception
    {
        float reflectance = 1f;       
        int maxParticles = 200;
        Vector3f particleSpeed = new Vector3f(0, 1, 0);
        particleSpeed.mul(2.5f);
        long ttl = 4000;
        long creationPeriodMillis = 100;
        float range = 12f;
        float scale = .5f;
        Mesh partMesh = OBJLoader.loadMesh("models/particle.obj", maxParticles);
        Texture particleTexture = new Texture("textures/particle_tmp.png");
        Material partMaterial = new Material(particleTexture, reflectance);
        partMesh.setMaterial(partMaterial);
        Particle particle = new Particle(partMesh, particleSpeed, ttl, 100);
		particle.getPosition().set(victoryPlatform.getPosition());
        particle.setScale(scale);
        particleEmitter = new FlowParticleEmitter(particle, maxParticles, creationPeriodMillis);
        particleEmitter.setPositionRndRange(range);
        particleEmitter.setSpeedRndRange(range);
        particleEmitter.setAnimRange(10);
		scene.setParticleEmitters(new FlowParticleEmitter[]{particleEmitter});
    }
}

