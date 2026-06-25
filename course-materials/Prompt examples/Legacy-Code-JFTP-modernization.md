# Example prompt (dictated) to modernize legacy code

> Repo legacy: https://github.com/sai-pullabhotla/jftp/tree/master#

You are in a very old Java project, in very old versions, which are no longer supported and which will not work here locally on this computer.

I want you to help me modernize this code and create a detailed plan for how we can do it step by step, so that this project works with modern versions of Java and with newer versions of libraries, and above all, so that the app runs and we can use it again (without changing or adding functionalities).

As part of this plan, it suggests that you take the following steps.
1. First of all, at the beginning analyze all the code and make detailed documentation for how this code looks at this moment. Describe all dependencies, describe versions, describe how it works, describe the architecture, make detailed documentation in multiple files. Also create AGENTS.md and CLAUDE.md (importing @AGENTS.md) files that will describe specifically for the agents how they can work in this project, what is here, and what the structure of this project is.
It is very important that you emphasize in the AGENTS.md instructions for the agents that these files must be updated during the execution of the plan and during the modernization of the application, so that AGENTS.md instructions are continuously aligned with how this project actually looks after changes. Documentation of detailed parts of the application and how it all works should be saved in multiple files in Markdown in the /docs folder. However, documentation for the agents in AGENTS.md / CLAUDE.md files should be split into multiple specialized files in the individual parts of the system in nested folders for each specific part of the application. Make one general AGENTS.md file at the main level of the project, whereas the detailed descriptions and instructions for the agents in the individual parts of the project, make it already inside deeper folders, concerning only specific code fragments data.

2. First, do detailed tests regarding the example four functionalities of this application; these can be unit tests that will check whether this application continues to work after our refactor. In these tests, focus on functionality, not on implementation first of all. That is, at the moment when we do the refactor, regardless of the implementation change, the test should still pass if the functionality works. 

3. Try to run this application with minimal changes. Introduce such changes that will be minimally needed for this application to start at the beginning. 

4. After the minimum startup and after the minimum changes needed to run this application, of course check whether the tests still pass. If they do not pass, you must fix something in the application and check whether this functionality was actually broken. Focus on fixing the code, not on fixing the tests. Assume initially that your modified code is wrong, not the tests.

5. At the moment when the application is being launched, and we have tests passing, in such a minimal version, after a minimal refactor of this application, you should make granular commits and move on to the proper larger refactor, updating the versions of various dependencies and also updating the code itself to newer Java standards, so that it is implemented in accordance with the latest guidelines related to Java and modern standards.
