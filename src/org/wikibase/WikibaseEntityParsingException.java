package org.wikibase;

public class WikibaseEntityParsingException extends Exception
{
    private String parsedText;
    
    public WikibaseEntityParsingException(Throwable cause, String parsedText)
    {
        super(cause.getMessage(), cause);
        this.parsedText = parsedText;
    }
    
    public String getParsedText()
    {
        return parsedText;
    }
}
