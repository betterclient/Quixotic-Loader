# Quixotic Wrapper

A java wrapper that enable usage of mixins.

### Usage 

````groovy
maven { url 'https://jitpack.io' }

dependencies {
    implementation 'com.github.betterclient:Quixotic-Loader:Tag'
}
````

Replace `Tag` with the build number.

#### Create a new class for your application.

````java
public class ExampleApplication implements QuixoticApplication {
    @Override
    public String getApplicationName() {
        return "AppName";
    }

    @Override
    public String getApplicationVersion() {
        return "appversion";
    }

    @Override
    public String getMainClass() {
        return "app.MainClass";
    }

    @Override
    public void loadApplicationManager(QuixoticClassLoader classLoader) {
        
    }

    @Override
    public List<String> getMixinConfigurations() {
        return new ArrayList<>();
    }
}
````

> What to do now?

> 1: You can add any mixin configurations using the `getMixinConfigurations` method's return. 

> 2: Change the `getMainClass` method to return your application's main class. 

> 3: Load any ClassTransformer's you want using `loadApplicationManager`. 

> 4: Last of all change your app's version and name.

#### Adding the quixoticapp argument

> This step is optional if you want to use the MinecraftVanillaApplication or already have it in the arguments.

> You can do the following using the Quixotic class as the main class. 

#### Create the java main to add your application class

````java
import java.util.ArrayList;
import java.util.Arrays;

public class ExampleMain {
    public static void main(String[] args) {
        ArrayList<String> arguments = new ArrayList<>(Arrays.asList(args));
        
        arguments.add("--quixoticapp");
        arguments.add("com.example.ExampleApplication");
        
        Quixotic.main(arguments.toArray(new String[0]));
    }
}
````

> Launch the app using the main class you just made.
> All done!