# Khan Academy PyCharm Plugin

This is a plugin for the PyCharm IDE intended to be used by developers working at Khan Academy.

Currently, it has one feature:

* It replaces the normal "Run test" context menu options with similar menu options that create run
configurations compatible with Khan Academy's test infrastructure.

## Installing the plugin

Download the latest jar from the
["Getting Started" Forge page](https://sites.google.com/a/khanacademy.org/forge/for-developers/using-pycharm-at-khan-academy/getting-started-with-pycharm)
In the PyCharm preferences, go to the Plugins section, and click "Install Plugin from Disk", then
navigate to the JAR from that page.

## Making changes to the plugin

Probably the best approach is to ask Alan.

For now, here are some instructions from memory that may be helpful:

1. Install IntelliJ IDEA Community Edition.
2. Open the repo as an IntelliJ project.
3. Choose your PyCharm installation as the plugin development SDK.
4. Make a run configuration form within IntelliJ and run it the plugin from within IntelliJ. If
things work, it will launch a fresh PyCharm instance with the plugin installed, which you can use
for testing.
5. Run the "Prepare To Deploy" action. It will produce a KAPyCharmPlugin.jar file, which is the
plugin binary.
6. To learn the API, the best approach is to get the high-level details and possibilities from the
[Plugin Development Wiki](http://confluence.jetbrains.com/display/IDEADEV/PluginDevelopment) (and
other Googlable resources), and to figure out the full API details by digging into the source code.
You can clone the source code from the
[IntelliJ IDEA Community Edition GitHub Project](https://github.com/JetBrains/intellij-community)
(which also contains the PyCharm Community Edition source code). In addition to looking through it
as a separate IntelliJ project, you can set up source mappings for the plugin project by adding the
source code to the "Sourcepath" part of the PyCharm SDK in the Project Structure dialog.