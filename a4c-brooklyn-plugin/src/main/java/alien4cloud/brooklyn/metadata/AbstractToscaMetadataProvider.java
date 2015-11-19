package alien4cloud.brooklyn.metadata;

public abstract class AbstractToscaMetadataProvider{

    private AbstractToscaMetadataProvider next;

    public void setNext(AbstractToscaMetadataProvider next){
        this.next = next;
    };

    public AbstractToscaMetadataProvider next(){
        return this.next;
    }

    public boolean hasNext(){
        return next != null;
    }

    public abstract String findToscaType(String type);

}
