# To learn more about how to use Nix to configure your environment
# see: https://developers.google.com/idx/guides/customize-workspace-env
{ pkgs, ... }: {
  # Which nixpkgs channel to use.
  channel = "stable-24.11";

  # Packages to install in the environment.
  packages = [
    pkgs.jdk21
    pkgs.gradle
    pkgs.android-tools
  ];

  # Sets environment variables in the workspace
  env = {
    JAVA_HOME = "${pkgs.jdk21}/lib/openjdk";
  };

  idx = {
    # Search for the extensions you want on https://open-vsx.org/ and use "publisher.id"
    extensions = [
      "mathiasfrohlich.Kotlin"
    ];

    # Enable previews and configure how to run the app
    previews = {
      enable = true;
      previews = {
        android = {
          command = ["./gradlew" "assembleDebug"];
          manager = "android";
        };
      };
    };
  };
}
